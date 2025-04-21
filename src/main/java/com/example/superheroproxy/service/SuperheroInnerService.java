package com.example.superheroproxy.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.config.CacheConfig;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.UpdateType;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

/**
 * The core service that handles superhero data operations and caching.
 * This service:
 * - Manages the caching of hero search results and hero details
 * - Coordinates with the external API service for data retrieval
 * - Monitors hero data for updates
 * - Notifies subscribers about hero changes
 * 
 * The service uses Spring's caching mechanism to improve performance
 * and reduce external API calls. It also integrates with the notification
 * system to keep clients updated about hero data changes.
 */
@Service
public class SuperheroInnerService {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroInnerService.class);

    private final HeroCheckScheduleService heroCheckScheduleService;
    private final NotificationService notificationService;
    private final ExternalApiService externalAPIService;
    private final ConcurrentHashMap<String, AtomicInteger> searchCounters;
    private final ConcurrentHashMap<String, AtomicInteger> heroCounters;
    private final ConcurrentHashMap<String, Set<String>> searchCache;
    private final ConcurrentHashMap<String, Hero> heroCache;

    /**
     * Constructs a new SuperheroInnerService with the required dependencies.
     * 
     * @param restTemplate The RestTemplate for making HTTP requests
     * @param heroCheckScheduleService Service for managing cache updates
     * @param notificationService Service for notifying clients about hero updates
     * @param externalAPIService Service for interacting with the external superhero API
     */
    public SuperheroInnerService(
            RestTemplate restTemplate,
            HeroCheckScheduleService heroCheckScheduleService,
            NotificationService notificationService,
            ExternalApiService externalAPIService) {
        this.heroCheckScheduleService = heroCheckScheduleService;
        this.notificationService = notificationService;
        this.externalAPIService = externalAPIService;
        this.searchCounters = new ConcurrentHashMap<>();
        this.heroCounters = new ConcurrentHashMap<>();
        this.searchCache = new ConcurrentHashMap<>();
        this.heroCache = new ConcurrentHashMap<>();
        
    }

    /**
     * Searches for hero IDs by name, with results cached for performance.
     * The search is case-insensitive and uses Spring's caching mechanism.
     * 
     * @param name The hero name to search for
     * @return A set of hero IDs matching the search criteria
     * @throws RuntimeException if there's an error during the search
     */
    @Cacheable(value = CacheConfig.HERO_SEARCH_CACHE, key = "#name.toLowerCase()")
    public Set<String> searchHeroIds(String name) {
        String normalizedName = name.toLowerCase();
        

        // If the name might exist (or is a false positive), perform the search
        Set<String> result = performSearch(normalizedName);
        
        return result;
    }

    private Set<String> performSearch(String normalizedName) {
        AtomicInteger counter = searchCounters.computeIfAbsent(normalizedName, k -> new AtomicInteger(0));
        counter.incrementAndGet();
        
        try {
            return searchCache.computeIfAbsent(normalizedName, key -> {
                try {
                    SearchResponse searchResponse = externalAPIService.searchHero(key);
                    return searchResponse.getResultsList().stream()
                            .map(e -> e.getId())
                            .collect(Collectors.toSet());
                } catch (Exception e) {
                    logger.error("Error searching for hero: {}", key, e);
                    return null; // Will be cached as null
                }
            });
        } finally {
            if (counter.decrementAndGet() == 0) {
                searchCounters.remove(normalizedName);
                searchCache.remove(normalizedName);
            }
        }
    }

    /**
     * Retrieves detailed information about a specific hero by ID.
     * The hero data is cached for performance and the hero is registered
     * for monitoring updates.
     * 
     * @param id The ID of the hero to retrieve
     * @return The Hero object containing detailed information
     * @throws RuntimeException if there's an error retrieving the hero data
     */
    @Cacheable(value = CacheConfig.SUPERHERO_CACHE, key = "#id" /*, unless = "#result==null || #result.getId().isEmpty()"*/)
    public Hero getHero(String id) {
        AtomicInteger counter = heroCounters.computeIfAbsent(id, k -> new AtomicInteger(0));
        counter.incrementAndGet();
        
        try {
            return heroCache.computeIfAbsent(id, key -> {
                try {
                    // Register the hero for monitoring
                    heroCheckScheduleService.addHeroToMonitor(key);
                    Hero hero = externalAPIService.getHero(key);

                    if (hero != null) {
                        // Notify subscribers about the initial data
                        notificationService.notifyHeroUpdate(key, hero, UpdateType.NEW);
                    }

                    return hero;
                } catch (Exception e) {
                    logger.error("Error searching for hero: {}", key, e);
                    return null; // Will be cached as null
                }
            });
        } finally {
            if (counter.decrementAndGet() == 0) {
                heroCounters.remove(id);
                heroCache.remove(id);
            }
        }
    }
} 