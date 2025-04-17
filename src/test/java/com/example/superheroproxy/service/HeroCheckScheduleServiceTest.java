package com.example.superheroproxy.service;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.config.CacheConfig;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.UpdateType;
import com.example.superheroproxy.utils.ResponseGenerator;

@ExtendWith(MockitoExtension.class)
class HeroCheckScheduleServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ExternalApiService externalAPIService;

    @Mock
    private RestTemplate restTemplate;

    private HeroCheckScheduleService heroCheckScheduleService;
    private static final String MOCK_RESPONSE = "{\"response\":\"success\",\"id\":\"621\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"55\",\"speed\":\"60\",\"durability\":\"75\",\"power\":\"74\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Peter Parker\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Spidey\",\"Wall-crawler\",\"Web-slinger\"],\"place-of-birth\":\"New York, New York\",\"first-appearance\":\"Amazing Fantasy #15\",\"publisher\":\"Marvel Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Human\",\"height\":[\"5'10\",\"178 cm\"],\"weight\":[\"165 lb\",\"75 kg\"],\"eye-color\":\"Hazel\",\"hair-color\":\"Brown\"},\"work\":{\"occupation\":\"Freelance photographer, teacher\",\"base\":\"New York, New York\"},\"connections\":{\"group-affiliation\":\"Avengers\",\"relatives\":\"Richard and Mary Parker (parents, deceased)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}";

    @BeforeEach
    void setUp() {
//        when(cacheManager.getCache(CacheConfig.SUPERHERO_CACHE)).thenReturn(cache);
        heroCheckScheduleService = new HeroCheckScheduleService(
            cacheManager,
            notificationService,
            externalAPIService,
            restTemplate
        );
    }

    @Test
    void testAddHeroToMonitor() throws Exception {
        // Given
        String heroId = "621";
        Hero mockHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        when(externalAPIService.getHero(heroId)).thenReturn(mockHero);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("| 620 | Spider-Man |");
        when(cacheManager.getCache(CacheConfig.SUPERHERO_CACHE)).thenReturn(cache);

        // When
        heroCheckScheduleService.addHeroToMonitor(heroId);
        heroCheckScheduleService.checkForUpdates();

        // Wait for async operations to complete
        Thread.sleep(100); // Small delay to allow async operations to complete

        // Then
        verify(externalAPIService).getHero(heroId);
        verify(cache).put(eq(heroId), eq(mockHero));
        verify(notificationService).notifyHeroUpdate(eq(heroId), eq(mockHero), eq(UpdateType.NEW));
    }

    @Test
    void testProcessHeroUpdate_NewHero() throws Exception {
        // Given
        String heroId = "621";
        Hero mockHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        
        when(externalAPIService.getHero(heroId)).thenReturn(mockHero);
        when(cache.get(eq(heroId), eq(Hero.class))).thenReturn(null);

        // When
        CompletableFuture<Void> future = heroCheckScheduleService.processHeroUpdate(heroId, cache);
        future.get(); // Wait for completion

        // Then
        verify(cache).put(heroId, mockHero);
        verify(notificationService).notifyHeroUpdate(heroId, mockHero, UpdateType.NEW);
    }

    @Test
    void testProcessHeroUpdate_UpdatedHero() throws Exception {
        // Given
        String heroId = "621";
        Hero oldHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        Hero newHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        newHero = newHero.toBuilder().setName("Updated Spider-Man").build();
        
        when(externalAPIService.getHero(heroId)).thenReturn(newHero);
        when(cache.get(eq(heroId), eq(Hero.class))).thenReturn(oldHero);

        // When
        CompletableFuture<Void> future = heroCheckScheduleService.processHeroUpdate(heroId, cache);
        future.get(); // Wait for completion

        // Then
        verify(cache).put(heroId, newHero);
        verify(notificationService).notifyHeroUpdate(heroId, newHero, UpdateType.UPDATED);
    }

    @Test
    void testProcessHeroUpdate_NoChanges() throws Exception {
        // Given
        String heroId = "621";
        Hero mockHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        
        when(externalAPIService.getHero(heroId)).thenReturn(mockHero);
        when(cache.get(eq(heroId), eq(Hero.class))).thenReturn(mockHero);

        // When
        CompletableFuture<Void> future = heroCheckScheduleService.processHeroUpdate(heroId, cache);
        future.get(); // Wait for completion

        // Then
        verify(cache, never()).put(anyString(), any());
        verify(notificationService, never()).notifyHeroUpdate(anyString(), any(), any());
    }

    @Test
    void testProcessHeroUpdate_ErrorHandling() throws Exception {
        // Given
        String heroId = "621";
        when(externalAPIService.getHero(heroId)).thenThrow(new RuntimeException("API Error"));

        // When
        CompletableFuture<Void> future = heroCheckScheduleService.processHeroUpdate(heroId, cache);
        
        // Then
        assertThrows(Exception.class, () -> future.get());
        verify(cache, never()).put(anyString(), any());
        verify(notificationService, never()).notifyHeroUpdate(anyString(), any(), any());
    }

    @Test
    void testCheckForUpdates_WithNewHeroes() throws Exception {
        // Given
        String heroId = "621";
        String htmlContent = "| 621 | Spider-Man |";
        Hero mockHero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        
        when(cacheManager.getCache(CacheConfig.SUPERHERO_CACHE)).thenReturn(cache);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(htmlContent);
        when(externalAPIService.getHero(heroId)).thenReturn(mockHero);
        when(cache.get(eq(heroId), eq(Hero.class))).thenReturn(null);

        // When
        heroCheckScheduleService.addHeroToMonitor(heroId); // Explicitly add hero to monitoring
        heroCheckScheduleService.checkForUpdates();
        
        // Wait for async operations to complete
        Thread.sleep(1000);

        // Then
        verify(externalAPIService).getHero(heroId);
        verify(cache).put(eq(heroId), eq(mockHero));
        verify(notificationService).notifyHeroUpdate(eq(heroId), eq(mockHero), eq(UpdateType.NEW));
    }

    @Test
    void testCheckForUpdates_WithError() throws Exception {
        // Given
        String heroId = "621";
        String htmlContent = "| 621 | Spider-Man |";
        when(cacheManager.getCache(CacheConfig.SUPERHERO_CACHE)).thenReturn(cache);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(htmlContent);
        when(externalAPIService.getHero(heroId)).thenThrow(new RuntimeException("API Error"));

        // When
        heroCheckScheduleService.addHeroToMonitor(heroId); // Explicitly add hero to monitoring
        heroCheckScheduleService.checkForUpdates();
        
        // Wait for async operations to complete
        Thread.sleep(1000);

        // Then
        verify(externalAPIService).getHero(heroId);
        verify(cache, never()).put(anyString(), any());
        verify(notificationService, never()).notifyHeroUpdate(anyString(), any(), any());
    }

} 