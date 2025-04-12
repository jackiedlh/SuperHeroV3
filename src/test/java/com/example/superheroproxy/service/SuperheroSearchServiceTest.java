package com.example.superheroproxy.service;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.SearchResponse;

@SpringJUnitConfig(classes = SuperheroSearchServiceTest.TestConfig.class)
class SuperheroSearchServiceTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager cacheManager = new SimpleCacheManager();
            cacheManager.setCaches(Collections.singletonList(
                new ConcurrentMapCache("superheroCache")
            ));
            return cacheManager;
        }

        @Bean
        RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        CacheUpdateService cacheUpdateService() {
            return mock(CacheUpdateService.class);
        }

        @Bean
        SuperheroSearchService superheroSearchService(RestTemplate restTemplate, CacheUpdateService cacheUpdateService, CacheManager cacheManager) {
            SuperheroSearchService service = new SuperheroSearchService(restTemplate, cacheUpdateService);
            service.setApiToken("test-token");
            return service;
        }
    }

    @Autowired
    private SuperheroSearchService superheroSearchService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheUpdateService cacheUpdateService;

    @BeforeEach
    void setUp() {
        // Setup API response
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(
            "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}"
        );
    }

    @Test
    void testSearchHero_CacheHit() throws Exception {
        // First request (cache miss)
        SearchResponse response1 = superheroSearchService.searchHero("spider-man");
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));

        // Second request (cache hit)
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");

        // Verify that the RestTemplate was only called once in total
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));

        // Verify response content
        assertEquals(response1.getResponse(), response2.getResponse());
        assertEquals(response1.getResultsFor(), response2.getResultsFor());
        assertEquals(response1.getResultsCount(), response2.getResultsCount());
    }

    @Test
    void testSearchHero_CaseInsensitive() throws Exception {
        // First request with mixed case (cache miss)
        SearchResponse response1 = superheroSearchService.searchHero("Spider-Man");
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));

        // Second request with lowercase (cache hit)
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");

        // Verify that the RestTemplate was only called once in total
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));

        // Verify response content
        assertEquals(response1.getResponse(), response2.getResponse());
        assertEquals(response1.getResultsFor(), response2.getResultsFor());
        assertEquals(response1.getResultsCount(), response2.getResultsCount());
    }
} 