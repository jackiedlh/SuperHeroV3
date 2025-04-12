package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

@Service
public class CacheUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(CacheUpdateService.class);

    @Value("${superhero.api.url}")
    private String apiUrl;

    @Value("${superhero.api.token}")
    private String apiToken;

    // Public setter for testing
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    @Value("${superhero.cache.update.interval:3600}")
    private long updateIntervalSeconds;

    private final RestTemplate restTemplate;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final Set<String> monitoredHeroes;
    private final NotificationServiceImpl notificationService;

    public CacheUpdateService(
            RestTemplate restTemplate,
            CacheManager cacheManager,
            NotificationServiceImpl notificationService
           ) {
        this.restTemplate = restTemplate;
        this.cacheManager = cacheManager;
        this.objectMapper = new ObjectMapper();
        this.monitoredHeroes = new ConcurrentSkipListSet<>();
        this.notificationService = notificationService;
    }

    public void addHeroToMonitor(String heroName) {
        monitoredHeroes.add(heroName.toLowerCase());
    }

    @Scheduled(fixedRateString = "${superhero.cache.update.interval:3600}", timeUnit = TimeUnit.SECONDS)
    public void updateCache() {
        logger.info("Starting scheduled cache update for {} heroes", monitoredHeroes.size());
        
        Cache cache = cacheManager.getCache("superheroCache");
        if (cache == null) {
            logger.error("Cache 'superheroCache' not found");
            return;
        }

        // Create a copy of monitoredHeroes to avoid concurrent modification
        for (String heroName : monitoredHeroes) {
            try {
                String url = String.format("%s/%s/search/%s", apiUrl, apiToken, heroName);
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                if (rootNode.has("results") && rootNode.get("results").isArray() && rootNode.get("results").size() > 0) {
                    // Get the current cached value
                    SearchResponse cachedResponse = cache.get(heroName, SearchResponse.class);

                    // Create new response
                    SearchResponse newResponse = ResponseGenerator.createSearchResponse(heroName, rootNode);

                    // If cached value exists and is different from new value, update the cache
                    if (cachedResponse == null || !cachedResponse.equals(newResponse)) {
                        cache.put(heroName, newResponse);
                        logger.info("Updated cache for hero: {}", heroName);
                        
                        // Notify subscribers about the update
                        for (Hero hero : newResponse.getResultsList()) {
                            notificationService.notifyHeroUpdate(
                                heroName,
                                hero
                            );
                        }
                    } else {
                        logger.debug("No changes detected for hero: {}", heroName);
                    }
                } else {
                    // If no hero found, remove from monitoring
                    monitoredHeroes.remove(heroName);
                    logger.info("Removed hero from monitoring (not found): {}", heroName);
                }
            } catch (Exception e) {
                logger.error("Error updating cache for hero: {}", heroName, e);
            }
        }

        // Cleanup: Remove heroes from monitoring if they're not in cache
        cleanupMonitoredHeroes();
    }

    private void cleanupMonitoredHeroes() {
        Cache cache = cacheManager.getCache("superheroCache");
        if (cache == null) {
            logger.error("Cache 'superheroCache' not found during cleanup");
            return;
        }

        // Create a copy of monitoredHeroes to avoid concurrent modification
        for (String heroName : new ConcurrentSkipListSet<>(monitoredHeroes)) {
            Cache.ValueWrapper value = cache.get(heroName);
            if (value == null) {
                // Hero is not in cache, remove from monitoring
                monitoredHeroes.remove(heroName);
                logger.info("Removed hero from monitoring (cache evicted): {}", heroName);
            }
        }
    }
} 