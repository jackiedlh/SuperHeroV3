package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SuperheroSearchService {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroSearchService.class);

    @Value("${superhero.api.token}")
    private String apiToken;

    @Value("${superhero.api.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CacheUpdateService cacheUpdateService;
    private final NotificationServiceImpl notificationService;
    private CacheManager cacheManager;

    public SuperheroSearchService(
            RestTemplate restTemplate, 
            CacheUpdateService cacheUpdateService,
            NotificationServiceImpl notificationService) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.cacheUpdateService = cacheUpdateService;
        this.notificationService = notificationService;
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
        logger.info("Cache miss for hero search: {}", name);
        String url = String.format("%s/%s/search/%s", baseUrl, apiToken, name);
        logger.debug("Making request to: {}", url);

        try {
            String response = restTemplate.getForObject(url, String.class);
            SearchResponse searchResponse = ResponseGenerator.createSearchResponse(name, response);
            
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
            Hero hero = getHeroInternal(id);

            // Notify subscribers about the initial data
            if (hero != null) {
                notificationService.notifyHeroUpdate(id, hero);
            }

            return hero;
        } catch (Exception e) {
            logger.error("Error searching for hero: {}", id, e);
            throw new RuntimeException("Failed to search for hero: " + id, e);
        }
    }

    public Hero getHeroInternal(String id) throws Exception {
        logger.info("Cache miss for hero id: {}", id);
        String url = String.format("%s/%s/%s", baseUrl.trim(), apiToken.trim(), id);
        logger.debug("Making request to URL: {}", url);

        String jsonResponse = restTemplate.getForObject(url, String.class);
        logger.debug("API Response: {}", jsonResponse);

        return ResponseGenerator.generateHero(jsonResponse);
    }

    public SearchResponse searchHeroInternal(String name) throws Exception {
        logger.info("Cache miss for hero: {}", name);
        String url = String.format("%s/%s/search/%s", baseUrl.trim(), apiToken.trim(), name);
        logger.debug("Making request to URL: {}", url);
        
        String jsonResponse = restTemplate.getForObject(url, String.class);
        logger.debug("API Response: {}", jsonResponse);

        return ResponseGenerator.createSearchResponse(name, jsonResponse);
    }
} 