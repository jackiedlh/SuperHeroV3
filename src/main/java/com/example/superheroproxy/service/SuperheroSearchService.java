package com.example.superheroproxy.service;

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
    private CacheManager cacheManager;

    public SuperheroSearchService(RestTemplate restTemplate, CacheUpdateService cacheUpdateService) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.cacheUpdateService = cacheUpdateService;
    }

    // Public setter for testing
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // Public setter for testing
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    @Cacheable(value = "superheroCache", key = "#name.toLowerCase()")
    public SearchResponse searchHero(String name) {
        try {
            // Register the hero for monitoring
            cacheUpdateService.addHeroToMonitor(name);
            return searchHeroInternal(name);
        } catch (Exception e) {
            logger.error("Error searching for hero: {}", name, e);
            throw new RuntimeException("Failed to search for hero: " + name, e);
        }
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