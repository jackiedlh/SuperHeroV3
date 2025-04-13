package com.example.superheroproxy.service;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;

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

import com.example.superheroproxy.config.TestRestTemplate;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;

@SpringJUnitConfig(classes = SuperheroSearchServiceTest.TestConfig.class)
class SuperheroSearchServiceTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        private String mockResponse = "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}";

        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager cacheManager = new SimpleCacheManager();
            cacheManager.setCaches(Arrays.asList(
                    new ConcurrentMapCache("superheroCache"), new ConcurrentMapCache("heroSearchCache")
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
        CacheUpdateService cacheUpdateService() {
            return mock(CacheUpdateService.class);
        }

        @Bean
        NotificationServiceImpl notificationService() {
            return mock(NotificationServiceImpl.class);
        }

        @Bean
        ExternalAPIService externalAPIService() {
            ExternalAPIService mockService = mock(ExternalAPIService.class);
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
        SuperheroSearchService superheroSearchService(
                TestRestTemplate restTemplate, 
                CacheUpdateService cacheUpdateService,
                NotificationServiceImpl notificationService,
                CacheManager cacheManager,
                ExternalAPIService externalAPIService) {
            SuperheroSearchService service = new SuperheroSearchService(restTemplate, cacheUpdateService, notificationService, externalAPIService);
            service.setApiToken("test-token");
            service.setCacheManager(cacheManager);
            return service;
        }
    }

    @Autowired
    private SuperheroSearchService superheroSearchService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheUpdateService cacheUpdateService;

    @Autowired
    private NotificationServiceImpl notificationService;

    @Autowired
    private ExternalAPIService externalAPIService;

    @BeforeEach
    void setUp() {
        // Setup API response
        restTemplate.setResponse(
            "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}"
        );
    }

    @Test
    void testSearchHero_CacheHit() throws Exception {
        // First request (cache miss)
        SearchResponse response1 = superheroSearchService.searchHero("spider-man");
        // Verify that the search API was called once
        verify(externalAPIService).searchHero("spider-man");

        // Second request (cache hit for individual heroes, cache also hit for search)
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");
        
        // Verify that the search API was called only once (since we cache search results)
        verify(externalAPIService).searchHero("spider-man");

        // Verify response content
        assertEquals(response1.getResponse(), response2.getResponse());
        assertEquals(response1.getResultsFor(), response2.getResultsFor());
        assertEquals(response1.getResultsCount(), response2.getResultsCount());
    }

    @Test
    void testSearchHero_CaseInsensitive() throws Exception {
        // First request with mixed case (cache miss)
        SearchResponse response1 = superheroSearchService.searchHero("Spider-Man");
        // Verify that the search API was called once
        verify(externalAPIService).searchHero("Spider-Man".toLowerCase());

        // Second request with lowercase (cache hit for individual heroes, and search cached too)
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");
        
        // Verify that the search API was called only once (since we cache search results)
        verify(externalAPIService).searchHero("Spider-Man".toLowerCase());

        // Verify response content
        assertEquals(response1.getResponse(), response2.getResponse());
        assertEquals(response1.getResultsFor(), response2.getResultsFor());
        assertEquals(response1.getResultsCount(), response2.getResultsCount());
    }
} 