package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.UpdateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SuperheroInnerService {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroInnerService.class);

    @Value("${superhero.api.token}")
    private String apiToken;

    @Value("${superhero.api.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CacheUpdateService cacheUpdateService;
    private final NotificationService notificationService;
    private CacheManager cacheManager;
    private final ExternalApiService externalAPIService;

    public SuperheroInnerService(
            RestTemplate restTemplate,
            CacheUpdateService cacheUpdateService,
            NotificationService notificationService,
            ExternalApiService externalAPIService) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.cacheUpdateService = cacheUpdateService;
        this.notificationService = notificationService;
        this.externalAPIService = externalAPIService;
    }

    // Public setter for testing
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // Public setter for testing
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    @Cacheable(value = "heroSearchCache", key = "#name.toLowerCase()")
    public SearchResponse searchHero(String name) {
//        logger.info("Cache miss for hero search: {}", name);
//        String url = String.format("%s/%s/search/%s", baseUrl, apiToken, name);
//        logger.debug("Making request to: {}", url);

        try {

            SearchResponse searchResponse = externalAPIService.searchHero(name);
            
            // Notify about the search
            //notificationService.notifySearch(name);
            
            return searchResponse;
        } catch (Exception e) {
            logger.error("Error searching for hero: {}", name, e);
            throw new RuntimeException("Error searching for hero", e);
        }
    }

    @Cacheable(value = "superheroCache", key = "#id")
    public Hero getHero(String id) {
        try {
            // Register the hero for monitoring
            cacheUpdateService.addHeroToMonitor(id);
            Hero hero = externalAPIService.getHero(id);

            // Notify subscribers about the initial data
            if (hero != null) {
                notificationService.notifyHeroUpdate(id, hero, UpdateType.NEW);
            }

            return hero;
        } catch (Exception e) {
            logger.error("Error searching for hero: {}", id, e);
            throw new RuntimeException("Failed to search for hero: " + id, e);
        }
    }




} 