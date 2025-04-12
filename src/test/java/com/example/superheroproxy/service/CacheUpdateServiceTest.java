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
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.SearchResponse;

public class CacheUpdateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private CacheUpdateService cacheUpdateService;
    private static final String API_URL = "https://superheroapi.com/api/1234567890123456";
    private static final String MOCK_RESPONSE = "{\"response\":\"success\",\"results\":[{\"id\":\"1\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"55\",\"speed\":\"60\",\"durability\":\"75\",\"power\":\"74\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Peter Parker\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Spidey\",\"Wall-crawler\",\"Web-slinger\"],\"place-of-birth\":\"New York, New York\",\"first-appearance\":\"Amazing Fantasy #15\",\"publisher\":\"Marvel Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Human\",\"height\":[\"5'10\",\"178 cm\"],\"weight\":[\"165 lb\",\"75 kg\"],\"eye-color\":\"Hazel\",\"hair-color\":\"Brown\"},\"work\":{\"occupation\":\"Freelance photographer, teacher\",\"base\":\"New York, New York\"},\"connections\":{\"group-affiliation\":\"Avengers\",\"relatives\":\"Richard and Mary Parker (parents, deceased)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        cacheUpdateService = new CacheUpdateService(restTemplate, cacheManager);
    }

    @Test
    void testAddHeroToMonitor() {
        String heroName = "spiderman";
        cacheUpdateService.addHeroToMonitor(heroName);
        // Verify the hero was added to monitoredHeroes
        // Note: Since monitoredHeroes is private, we can't directly verify it
        // Instead, we'll verify the behavior in updateCache
    }

    @Test
    void testUpdateCache() {
        String heroName = "spiderman";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(MOCK_RESPONSE);

        cacheUpdateService.addHeroToMonitor(heroName);
        cacheUpdateService.updateCache();

        verify(restTemplate).getForObject(anyString(), eq(String.class));
        verify(cache).put(eq(heroName), any(SearchResponse.class));
    }

    @Test
    void testUpdateCacheWithError() {
        String heroName = "spiderman";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new RuntimeException("API Error"));

        cacheUpdateService.addHeroToMonitor(heroName);
        cacheUpdateService.updateCache();

        verify(restTemplate).getForObject(anyString(), eq(String.class));
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void testUpdateCacheWithNoResults() {
        String heroName = "spiderman";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"response\":\"error\",\"results\":[]}");

        cacheUpdateService.addHeroToMonitor(heroName);
        cacheUpdateService.updateCache();

        verify(restTemplate).getForObject(anyString(), eq(String.class));
        verify(cache, never()).put(anyString(), any());
    }
} 