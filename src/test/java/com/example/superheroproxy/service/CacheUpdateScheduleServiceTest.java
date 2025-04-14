package com.example.superheroproxy.service;

import com.example.superheroproxy.config.TestRestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.utils.ResponseGenerator;

public class CacheUpdateScheduleServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ExternalApiService externalAPIService;

    private CacheUpdateScheduleService cacheUpdateScheduleService;
    private static final String MOCK_RESPONSE = "{\"response\":\"success\",\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"55\",\"speed\":\"60\",\"durability\":\"75\",\"power\":\"74\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Peter Parker\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Spidey\",\"Wall-crawler\",\"Web-slinger\"],\"place-of-birth\":\"New York, New York\",\"first-appearance\":\"Amazing Fantasy #15\",\"publisher\":\"Marvel Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Human\",\"height\":[\"5'10\",\"178 cm\"],\"weight\":[\"165 lb\",\"75 kg\"],\"eye-color\":\"Hazel\",\"hair-color\":\"Brown\"},\"work\":{\"occupation\":\"Freelance photographer, teacher\",\"base\":\"New York, New York\"},\"connections\":{\"group-affiliation\":\"Avengers\",\"relatives\":\"Richard and Mary Parker (parents, deceased)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        cacheUpdateScheduleService = new CacheUpdateScheduleService(cacheManager, notificationService, externalAPIService,  mock(TestRestTemplate.class));
    }

    @Test
    void testAddHeroToMonitor() {
        String heroId = "620"; // Spider-Man's ID
        cacheUpdateScheduleService.addHeroToMonitor(heroId);
        // Verify the hero was added to monitoredHeroes
        // Note: Since monitoredHeroes is private, we can't directly verify it
        // Instead, we'll verify the behavior in updateCache
    }

    @Test
    void testUpdateCache() throws Exception {
        String heroId = "620"; // Spider-Man's ID
        Hero mockHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        
        when(externalAPIService.getHero(heroId)).thenReturn(mockHero);
        when(cache.get(heroId, Hero.class)).thenReturn(null); // No cached value

        cacheUpdateScheduleService.addHeroToMonitor(heroId);
        cacheUpdateScheduleService.checkForUpdates();

        verify(externalAPIService).getHero(heroId);
        verify(cache).put(eq(heroId), any(Hero.class));
        verify(notificationService).notifyHeroUpdate(eq(heroId), any(Hero.class), any());
    }

    @Test
    void testUpdateCacheWithError() throws Exception {
        String heroId = "620"; // Spider-Man's ID
        when(externalAPIService.getHero(heroId)).thenThrow(new RuntimeException("API Error"));

        cacheUpdateScheduleService.addHeroToMonitor(heroId);
        cacheUpdateScheduleService.checkForUpdates();

        verify(externalAPIService).getHero(heroId);
        verify(cache, never()).put(anyString(), any());
        // Verify the hero was removed from monitoring
        // Note: Since monitoredHeroes is private, we can't directly verify it
    }

    @Test
    void testUpdateCacheNoChanges() throws Exception {
        String heroId = "620"; // Spider-Man's ID
        Hero mockHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        
        when(externalAPIService.getHero(heroId)).thenReturn(mockHero);
        when(cache.get(heroId, Hero.class)).thenReturn(mockHero); // Same hero in cache

        cacheUpdateScheduleService.addHeroToMonitor(heroId);
        cacheUpdateScheduleService.checkForUpdates();

        verify(externalAPIService).getHero(heroId);
        verify(cache, never()).put(anyString(), any());
        verify(notificationService, never()).notifyHeroUpdate(anyString(), any(),any());
    }
} 