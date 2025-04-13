package com.example.superheroproxy.service;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
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

@SpringJUnitConfig(classes = SuperheroSearchServiceTest.TestConfig.class)
class SuperheroSearchServiceTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
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
            template.setResponse("{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}");
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
        SuperheroSearchService superheroSearchService(
                TestRestTemplate restTemplate, 
                CacheUpdateService cacheUpdateService,
                NotificationServiceImpl notificationService,
                CacheManager cacheManager) {
            SuperheroSearchService service = new SuperheroSearchService(restTemplate, cacheUpdateService, notificationService);
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
        assertEquals(1, restTemplate.getCallCount());

        // Second request (cache hit for individual heroes, cache also hit for search)
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");
        
        // Verify that the search API was called twice (since we cache search results)
        assertEquals(1, restTemplate.getCallCount());

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
        assertEquals(1, restTemplate.getCallCount());

        // Second request with lowercase (cache hit for individual heroes, and serch cached too)
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");
        
        // Verify that the search API was called once (since we cache search results)
        assertEquals(1, restTemplate.getCallCount());

        // Verify response content
        assertEquals(response1.getResponse(), response2.getResponse());
        assertEquals(response1.getResultsFor(), response2.getResultsFor());
        assertEquals(response1.getResultsCount(), response2.getResultsCount());
    }
} 