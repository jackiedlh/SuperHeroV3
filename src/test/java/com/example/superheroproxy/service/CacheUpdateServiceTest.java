package com.example.superheroproxy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.utils.ResponseGenerator;

public class CacheUpdateServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ExternalApiService externalAPIService;

    private CacheUpdateService cacheUpdateService;
    private static final String MOCK_RESPONSE = "{\"response\":\"success\",\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"55\",\"speed\":\"60\",\"durability\":\"75\",\"power\":\"74\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Peter Parker\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Spidey\",\"Wall-crawler\",\"Web-slinger\"],\"place-of-birth\":\"New York, New York\",\"first-appearance\":\"Amazing Fantasy #15\",\"publisher\":\"Marvel Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Human\",\"height\":[\"5'10\",\"178 cm\"],\"weight\":[\"165 lb\",\"75 kg\"],\"eye-color\":\"Hazel\",\"hair-color\":\"Brown\"},\"work\":{\"occupation\":\"Freelance photographer, teacher\",\"base\":\"New York, New York\"},\"connections\":{\"group-affiliation\":\"Avengers\",\"relatives\":\"Richard and Mary Parker (parents, deceased)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        cacheUpdateService = new CacheUpdateService(cacheManager, notificationService, externalAPIService);
    }

    @Test
    void testAddHeroToMonitor() {
        String heroId = "620"; // Spider-Man's ID
        cacheUpdateService.addHeroToMonitor(heroId);
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

        cacheUpdateService.addHeroToMonitor(heroId);
        cacheUpdateService.updateCache();

        verify(externalAPIService).getHero(heroId);
        verify(cache).put(eq(heroId), any(Hero.class));
        verify(notificationService).notifyHeroUpdate(eq(heroId), any(Hero.class));
    }

    @Test
    void testUpdateCacheWithError() throws Exception {
        String heroId = "620"; // Spider-Man's ID
        when(externalAPIService.getHero(heroId)).thenThrow(new RuntimeException("API Error"));

        cacheUpdateService.addHeroToMonitor(heroId);
        cacheUpdateService.updateCache();

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

        cacheUpdateService.addHeroToMonitor(heroId);
        cacheUpdateService.updateCache();

        verify(externalAPIService).getHero(heroId);
        verify(cache, never()).put(anyString(), any());
        verify(notificationService, never()).notifyHeroUpdate(anyString(), any());
    }
} 