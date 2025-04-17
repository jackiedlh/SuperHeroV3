package com.example.superheroproxy.service;

import com.example.superheroproxy.config.TestRestTemplate;
import com.example.superheroproxy.utils.ResponseGenerator;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.UpdateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@SpringJUnitConfig
@TestPropertySource(properties = {
    "superhero.cache.update.interval=60"
})
class SuperheroInnerServiceTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                "heroSearchCache",
                "superheroCache"
            );
        }

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        public TestRestTemplate testRestTemplate() {
            return mock(TestRestTemplate.class);
        }

        @Bean
        public HeroCheckScheduleService cacheUpdateScheduleService() {
            return mock(HeroCheckScheduleService.class);
        }

        @Bean
        public NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        public ExternalApiService externalAPIService() {
            return mock(ExternalApiService.class);
        }

        @Bean
        public SuperheroInnerService superheroInnerService(
                TestRestTemplate restTemplate,
                HeroCheckScheduleService heroCheckScheduleService,
                NotificationService notificationService,
                ExternalApiService externalAPIService) {
            return new SuperheroInnerService(restTemplate, heroCheckScheduleService, notificationService, externalAPIService);
        }
    }

    private static final String MOCK_RESPONSE = "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HeroCheckScheduleService heroCheckScheduleService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ExternalApiService externalAPIService;

    @Autowired
    private SuperheroInnerService superheroInnerService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Reset mocks
        reset(externalAPIService, heroCheckScheduleService, notificationService);
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @Test
    void testSearchHeroIds_Success() throws Exception {
        // Setup mock response
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("spider-man", MOCK_RESPONSE)
        );

        // First request (cache miss)
        Set<String> ids = superheroInnerService.searchHeroIds("spider-man");
        
        // Verify that the search API was called once
        verify(externalAPIService).searchHero("spider-man");
        
        // Verify response content
        assertNotNull(ids);
        assertEquals(1, ids.size());
        assertTrue(ids.contains("620"));
    }

    @Test
    void testSearchHeroIds_CacheHit() throws Exception {
        // Setup mock response
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("spider-man", MOCK_RESPONSE)
        );

        // First request (cache miss)
        Set<String> ids1 = superheroInnerService.searchHeroIds("spider-man");
        verify(externalAPIService).searchHero("spider-man");

        // Second request (cache hit)
        Set<String> ids2 = superheroInnerService.searchHeroIds("spider-man");
        
        // Verify that the search API was called only once
        verify(externalAPIService).searchHero("spider-man");

        // Verify response content
        assertEquals(ids1, ids2);
    }

    @Test
    void testSearchHeroIds_CaseInsensitive() throws Exception {
        // Setup mock response
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("Spider-Man", MOCK_RESPONSE)
        );

        // First request with mixed case (cache miss)
        Set<String> ids1 = superheroInnerService.searchHeroIds("Spider-Man");
        verify(externalAPIService).searchHero("Spider-Man".toLowerCase());

        // Second request with lowercase (cache hit)
        Set<String> ids2 = superheroInnerService.searchHeroIds("spider-man");
        
        // Verify that the search API was called only once
        verify(externalAPIService).searchHero("Spider-Man".toLowerCase());

        // Verify response content
        assertEquals(ids1, ids2);
    }

    @Test
    void testSearchHeroIds_Error() throws Exception {
        // Mock an error response
        when(externalAPIService.searchHero(anyString())).thenThrow(new RuntimeException("API Error"));

        // Attempt to search
        Set<String> ids = superheroInnerService.searchHeroIds("spider-man");
        
        // Verify error handling
        assertNull(ids);
        verify(externalAPIService).searchHero("spider-man");
    }

    @Test
    void testSearchHeroIds_EmptyResults() throws Exception {
        // Mock empty results response
        String emptyResponse = "{\"response\":\"success\",\"results-for\":\"nonexistent\",\"results\":[]}";
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("nonexistent", emptyResponse)
        );

        // First request (should call API)
        Set<String> ids1 = superheroInnerService.searchHeroIds("nonexistent");
        verify(externalAPIService).searchHero("nonexistent");
        
        // Second request (should use empty result cache)
        Set<String> ids2 = superheroInnerService.searchHeroIds("nonexistent");
        verify(externalAPIService).searchHero("nonexistent"); // Still called once

        // Verify response
        assertNotNull(ids1);
        assertTrue(ids1.isEmpty());
        assertEquals(ids1, ids2);
    }

    @Test
    void testSearchHeroIds_ConcurrentRequests() throws Exception {
        // Create a latch to ensure all threads start at the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(5);

        // Mock a slow response to test concurrent requests
        when(externalAPIService.searchHero(anyString())).thenAnswer(invocation -> {
            Thread.sleep(100); // Simulate network delay
            return ResponseGenerator.createSearchResponse("spider-man", MOCK_RESPONSE);
        });

        // Create multiple threads to make concurrent requests
        Thread[] threads = new Thread[5];
        Set<String>[] results = new Set[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await(); // Wait for start signal
                    results[index] = superheroInnerService.searchHeroIds("spider-man");
                } catch (Exception e) {
                    fail("Thread " + index + " failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
            threads[i].start();
        }

        // Start all threads at the same time
        startLatch.countDown();

        // Wait for all threads to complete
        endLatch.await(5, TimeUnit.SECONDS);

        // Verify that all results are the same
        Set<String> firstResult = results[0];
        for (int i = 1; i < 5; i++) {
            assertEquals(firstResult, results[i]);
        }

        // Verify that the API was called only once
        verify(externalAPIService, times(1)).searchHero("spider-man");
    }

    @Test
    void testSearchHeroIds_BloomFilter_NonExistent() throws Exception {
        // Mock empty results response for a non-existent hero
        String emptyResponse = "{\"response\":\"success\",\"results-for\":\"nonexistent123\",\"results\":[]}";
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("nonexistent123", emptyResponse)
        );

        // First request - should call API and add to bloom filter
        Set<String> ids1 = superheroInnerService.searchHeroIds("nonexistent123");
        verify(externalAPIService).searchHero("nonexistent123");
        assertTrue(ids1.isEmpty());

        // Second request - should be caught by bloom filter without API call
        Set<String> ids2 = superheroInnerService.searchHeroIds("nonexistent123");
        verify(externalAPIService).searchHero("nonexistent123"); // Still only called once
        assertTrue(ids2.isEmpty());

        // Verify bloom filter is working by checking a similar non-existent name
        Set<String> ids3 = superheroInnerService.searchHeroIds("nonexistent123");
        verify(externalAPIService).searchHero("nonexistent123"); // Still only called once
        assertTrue(ids3.isEmpty());
    }

    @Test
    void testSearchHeroIds_BloomFilter_Existent() throws Exception {
        // Mock response for an existing hero
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("spider-man", MOCK_RESPONSE)
        );

        // First request - should call API and not add to bloom filter
        Set<String> ids1 = superheroInnerService.searchHeroIds("spider-man");
        verify(externalAPIService).searchHero("spider-man");
        assertFalse(ids1.isEmpty());
        assertEquals(1, ids1.size());
        assertTrue(ids1.contains("620"));

        // Second request - should use cache, not bloom filter
        Set<String> ids2 = superheroInnerService.searchHeroIds("spider-man");
        verify(externalAPIService).searchHero("spider-man"); // Still only called once
        assertFalse(ids2.isEmpty());
        assertEquals(ids1, ids2);
    }

    @Test
    void testSearchHeroIds_BloomFilter_FalsePositive() throws Exception {
        // Mock response for an existing hero
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("spider-man", MOCK_RESPONSE)
        );

        // First request - should call API and not add to bloom filter
        Set<String> ids1 = superheroInnerService.searchHeroIds("spider-man");
        verify(externalAPIService).searchHero("spider-man");
        assertFalse(ids1.isEmpty());

        // Second request - should use cache, not bloom filter
        Set<String> ids2 = superheroInnerService.searchHeroIds("spider-man");
        verify(externalAPIService).searchHero("spider-man"); // Still only called once
        assertFalse(ids2.isEmpty());
        assertEquals(ids1, ids2);

        // Verify bloom filter doesn't affect existing heroes
        Set<String> ids3 = superheroInnerService.searchHeroIds("spider-man");
        verify(externalAPIService).searchHero("spider-man"); // Still only called once
        assertFalse(ids3.isEmpty());
        assertEquals(ids1, ids3);
    }

    @Test
    void testGetHero_EmptyResultCaching() throws Exception {
        // Mock empty hero response
        when(externalAPIService.getHero(anyString())).thenReturn(null);

        // First request (should call API)
        Hero hero1 = superheroInnerService.getHero("nonexistent");
        verify(externalAPIService).getHero("nonexistent");
        
        // Second request (should use empty result cache)
        Hero hero2 = superheroInnerService.getHero("nonexistent");
        verify(externalAPIService).getHero("nonexistent"); // Still called once

        // Verify response
        assertNull(hero1);
        assertNull(hero2);
    }

    @Test
    void testGetHero_ConcurrentRequests() throws Exception {
        // Create a latch to ensure all threads start at the same time
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(5);

        // Mock a slow response to test concurrent requests
        when(externalAPIService.getHero(anyString())).thenAnswer(invocation -> {
            Thread.sleep(100); // Simulate network delay
            return Hero.newBuilder()
                    .setId("620")
                    .setName("Spider-Man")
                    .build();
        });

        // Create multiple threads to make concurrent requests
        Thread[] threads = new Thread[5];
        Hero[] results = new Hero[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await(); // Wait for start signal
                    results[index] = superheroInnerService.getHero("620");
                } catch (Exception e) {
                    fail("Thread " + index + " failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
            threads[i].start();
        }

        // Start all threads at the same time
        startLatch.countDown();

        // Wait for all threads to complete
        endLatch.await(5, TimeUnit.SECONDS);

        // Verify that all results are the same
        Hero firstResult = results[0];
        for (int i = 1; i < 5; i++) {
            assertEquals(firstResult, results[i]);
        }

        // Verify that the API was called only once
        verify(externalAPIService, times(1)).getHero("620");
    }

    @Test
    void testGetHero_Success() throws Exception {
        // Setup test data
        String heroId = "123";
        Hero mockHero = Hero.newBuilder()
            .setId(heroId)
            .setName("Batman")
            .build();

        // Mock external service behavior
        when(externalAPIService.getHero(heroId)).thenReturn(mockHero);

        // First call - should hit external API
        Hero result = superheroInnerService.getHero(heroId);
        assertNotNull(result);
        assertEquals(heroId, result.getId());

        // Verify external API was called and hero was registered for monitoring
        verify(externalAPIService, times(1)).getHero(heroId);
        verify(heroCheckScheduleService, times(1)).addHeroToMonitor(heroId);
        verify(notificationService, times(1)).notifyHeroUpdate(heroId, mockHero, UpdateType.NEW);

        // Second call - should use cache
        Hero cachedResult = superheroInnerService.getHero(heroId);
        assertNotNull(cachedResult);
        assertEquals(result, cachedResult);

        // Verify external API was not called again and no additional notifications
        verify(externalAPIService, times(1)).getHero(heroId);
        verify(heroCheckScheduleService, times(1)).addHeroToMonitor(heroId);
        verify(notificationService, times(1)).notifyHeroUpdate(heroId, mockHero, UpdateType.NEW);
    }

    @Test
    void testGetHero_Error() throws Exception {
        // Setup test data
        String heroId = "error123";
        RuntimeException testException = new RuntimeException("Test error");

        // Mock external service to throw exception
        when(externalAPIService.getHero(heroId)).thenThrow(testException);

        // Call should return null and cache the null result
        Hero result = superheroInnerService.getHero(heroId);
        assertNull(result);

        // Verify external API was called and hero was registered for monitoring
        verify(externalAPIService, times(1)).getHero(heroId);
        verify(heroCheckScheduleService, times(1)).addHeroToMonitor(heroId);
        verify(notificationService, never()).notifyHeroUpdate(anyString(), any(Hero.class), any(UpdateType.class));

        // Second call - should use cached null result
        Hero cachedResult = superheroInnerService.getHero(heroId);
        assertNull(cachedResult);

        // Verify external API was not called again
        verify(externalAPIService, times(1)).getHero(heroId);
    }

    @Test
    void testGetHero_NullHero() throws Exception {
        // Setup test data
        String heroId = "null123";

        // Mock external service to return null
        when(externalAPIService.getHero(heroId)).thenReturn(null);

        // Call should return null and cache the null result
        Hero result = superheroInnerService.getHero(heroId);
        assertNull(result);

        // Verify external API was called and hero was registered for monitoring
        verify(externalAPIService, times(1)).getHero(heroId);
        verify(heroCheckScheduleService, times(1)).addHeroToMonitor(heroId);
        verify(notificationService, never()).notifyHeroUpdate(anyString(), any(Hero.class), any(UpdateType.class));

        // Second call - should use cached null result
        Hero cachedResult = superheroInnerService.getHero(heroId);
        assertNull(cachedResult);

        // Verify external API was not called again
        verify(externalAPIService, times(1)).getHero(heroId);
    }
} 