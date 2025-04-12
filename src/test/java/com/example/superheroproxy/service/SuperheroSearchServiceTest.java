package com.example.superheroproxy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
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
            return new org.springframework.cache.caffeine.CaffeineCacheManager("superheroCache");
        }

        @Bean
        RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        SuperheroSearchService superheroSearchService(RestTemplate restTemplate) {
            SuperheroSearchService service = new SuperheroSearchService(restTemplate);
            service.setApiToken("test-token");
            return service;
        }
    }

    @Autowired
    private SuperheroSearchService superheroSearchService;

    @Autowired
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Reset the mock before each test
        when(restTemplate.getForObject(anyString(), any())).thenReturn(
            "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}"
        );
    }

    @Test
    void testSearchHero_CacheHit() throws Exception {
        // First request (cache miss)
        SearchResponse response1 = superheroSearchService.searchHero("spider-man");
        verify(restTemplate).getForObject(anyString(), any());

        // Second request (cache hit)
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");

        // Verify that the RestTemplate was only called once (cache hit for second call)
        verify(restTemplate, times(1)).getForObject(anyString(), any());

        // Verify response content
        assertEquals(response1.getResponse(), response2.getResponse());
        assertEquals(response1.getResultsFor(), response2.getResultsFor());
        assertEquals(response1.getResultsCount(), response2.getResultsCount());
    }

    @Test
    void testSearchHero_CaseInsensitive() throws Exception {
        // First request with mixed case
        SearchResponse response1 = superheroSearchService.searchHero("Spider-Man");
        verify(restTemplate).getForObject(anyString(), any());

        // Second request with lowercase
        SearchResponse response2 = superheroSearchService.searchHero("spider-man");

        // Verify that the RestTemplate was only called once (cache hit for second call)
        verify(restTemplate, times(1)).getForObject(anyString(), any());

        // Verify response content
        assertEquals(response1.getResponse(), response2.getResponse());
        assertEquals(response1.getResultsFor(), response2.getResultsFor());
        assertEquals(response1.getResultsCount(), response2.getResultsCount());
    }
} 