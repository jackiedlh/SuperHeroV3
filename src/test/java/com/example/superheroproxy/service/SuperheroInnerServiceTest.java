package com.example.superheroproxy.service;

import com.example.superheroproxy.config.TestRestTemplate;
import com.example.superheroproxy.utils.ResponseGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = SuperheroInnerServiceTest.TestConfig.class)
class SuperheroInnerServiceTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        private String mockResponse = "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}";

        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager cacheManager = new SimpleCacheManager();
            cacheManager.setCaches(Arrays.asList(
                    new ConcurrentMapCache("superheroCache"), 
                    new ConcurrentMapCache("heroSearchCache")
            ));
            return cacheManager;
        }

        @Bean
        TestRestTemplate restTemplate() {
            TestRestTemplate template = new TestRestTemplate();
            template.setResponse(mockResponse);
            return template;
        }

        @Bean
        CacheUpdateScheduleService cacheUpdateService() {
            return mock(CacheUpdateScheduleService.class);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        ExternalApiService externalAPIService() {
            ExternalApiService mockService = mock(ExternalApiService.class);
            try {
                when(mockService.searchHero(anyString())).thenAnswer(invocation -> {
                    String name = invocation.getArgument(0);
                    return ResponseGenerator.createSearchResponse(name, mockResponse);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return mockService;
        }

        @Bean
        SuperheroInnerService superheroSearchService(
                TestRestTemplate restTemplate, 
                CacheUpdateScheduleService cacheUpdateScheduleService,
                NotificationService notificationService,
                CacheManager cacheManager,
                ExternalApiService externalAPIService) {
            SuperheroInnerService service = new SuperheroInnerService(restTemplate, cacheUpdateScheduleService, notificationService, externalAPIService);
            return service;
        }
    }

    @Autowired
    private SuperheroInnerService superheroInnerService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheUpdateScheduleService cacheUpdateScheduleService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ExternalApiService externalAPIService;

    @BeforeEach
    void setUp() {
        // Clear caches before each test
        cacheManager.getCache("superheroCache").clear();
        cacheManager.getCache("heroSearchCache").clear();
        
        // Reset mock responses
        restTemplate.setResponse(
            "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}"
        );
    }

    @Test
    void testSearchHeroIds_Success() throws Exception {
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
        // First request with mixed case (cache miss)
        Set<String> ids1 = superheroInnerService.searchHeroIds("Spider-Man");
        verify(externalAPIService).searchHero("Spider-Man");

        // Second request with lowercase (cache hit)
        Set<String> ids2 = superheroInnerService.searchHeroIds("spider-man");
        
        // Verify that the search API was called only once
        verify(externalAPIService).searchHero("Spider-Man");

        // Verify response content
        assertEquals(ids1, ids2);
    }

    @Test
    void testSearchHeroIds_Error() throws Exception {
        // Mock an error response
        when(externalAPIService.searchHero(anyString())).thenThrow(new RuntimeException("API Error"));

        // Attempt to search
        Exception exception = assertThrows(RuntimeException.class, () -> {
            superheroInnerService.searchHeroIds("spider-man");
        });

        // Verify error handling
        assertTrue(exception.getMessage().contains("Error searching for hero"));
        verify(externalAPIService).searchHero("spider-man");
    }

    @Test
    void testSearchHeroIds_EmptyResults() throws Exception {
        // Mock empty results response
        String emptyResponse = "{\"response\":\"success\",\"results-for\":\"nonexistent\",\"results\":[]}";
        when(externalAPIService.searchHero(anyString())).thenReturn(
            ResponseGenerator.createSearchResponse("nonexistent", emptyResponse)
        );

        // Attempt to search
        Set<String> ids = superheroInnerService.searchHeroIds("nonexistent");
        
        // Verify response
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }
} 