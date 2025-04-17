package com.example.superheroproxy.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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

    private final CacheUpdateScheduleService cacheUpdateScheduleService;
    private final NotificationService notificationService;
    private final ExternalApiService externalAPIService;
    private final ConcurrentHashMap<String, ReentrantLock> searchLocks;
    private final ConcurrentHashMap<String, ReentrantLock> heroLocks;
    private final ConcurrentHashMap<String, Set<String>> searchCache;
    private final ConcurrentHashMap<String, Hero> heroCache;
    private final BloomFilter<String> nonExistentHeroFilter;

    /**
     * Constructs a new SuperheroInnerService with the required dependencies.
     * 
     * @param restTemplate The RestTemplate for making HTTP requests
     * @param cacheUpdateScheduleService Service for managing cache updates
     * @param notificationService Service for notifying clients about hero updates
     * @param externalAPIService Service for interacting with the external superhero API
     */
    public SuperheroInnerService(
            RestTemplate restTemplate,
            CacheUpdateScheduleService cacheUpdateScheduleService,
            NotificationService notificationService,
            ExternalApiService externalAPIService) {
        this.cacheUpdateScheduleService = cacheUpdateScheduleService;
        this.notificationService = notificationService;
        this.externalAPIService = externalAPIService;
        this.searchLocks = new ConcurrentHashMap<>();
        this.heroLocks = new ConcurrentHashMap<>();
        this.searchCache = new ConcurrentHashMap<>();
        this.heroCache = new ConcurrentHashMap<>();
        
        // Initialize bloom filter with expected 1000 items and 1% false positive rate
        this.nonExistentHeroFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            1000,
            0.01
        );
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
        
        // Check bloom filter first - if it says the name doesn't exist, return empty set
        if (nonExistentHeroFilter.mightContain(normalizedName)) {
            logger.debug("Bloom filter indicates hero name {} may not exist", normalizedName);
            return Set.of();
        }
        
        ReentrantLock lock = searchLocks.computeIfAbsent(normalizedName, k -> new ReentrantLock());
        
        try {
            lock.lock();
            return searchCache.computeIfAbsent(normalizedName, key -> {
                try {
                    SearchResponse searchResponse = externalAPIService.searchHero(key);
                    Set<String> result = searchResponse.getResultsList().stream()
                            .map(e -> e.getId())
                            .collect(Collectors.toSet());
                    
                    // If no results found, add to bloom filter
                    if (result.isEmpty()) {
                        nonExistentHeroFilter.put(key);
                        logger.debug("Added non-existent hero name {} to bloom filter", key);
                    }
                    
                    return result;
                } catch (Exception e) {
                    logger.error("Error searching for hero: {}", key, e);
                    return null; // Will be cached as null
                }
            });
        } finally {
            lock.unlock();
            // Clean up if no one is waiting
            if (!lock.hasQueuedThreads()) {
                searchLocks.remove(normalizedName);
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
    @Cacheable(value = CacheConfig.SUPERHERO_CACHE, key = "#id")
    public Hero getHero(String id) {
        ReentrantLock lock = heroLocks.computeIfAbsent(id, k -> new ReentrantLock());
        
        try {
            lock.lock();
            return heroCache.computeIfAbsent(id, key -> {
                try {
                    // Register the hero for monitoring
                    cacheUpdateScheduleService.addHeroToMonitor(key);
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
            lock.unlock();
            // Clean up if no one is waiting
            if (!lock.hasQueuedThreads()) {
                heroLocks.remove(id);
                heroCache.remove(id);
            }
        }
    }
} 