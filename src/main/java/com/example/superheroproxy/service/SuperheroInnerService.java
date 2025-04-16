package com.example.superheroproxy.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

import com.example.superheroproxy.config.CacheConfig;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.UpdateType;

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
    
    // Bloom filter to track non-existent hero names
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
        
        // Check local cache first
        Set<String> cachedResult = searchCache.get(normalizedName);
        if (cachedResult != null) {
            return cachedResult;
        }

        ReentrantLock lock = searchLocks.computeIfAbsent(normalizedName, k -> new ReentrantLock());
        
        try {
            lock.lock();
            try {
                // Double-check cache after acquiring lock
                cachedResult = searchCache.get(normalizedName);
                if (cachedResult != null) {
                    return cachedResult;
                }

                SearchResponse searchResponse = externalAPIService.searchHero(name);
                Set<String> result = searchResponse.getResultsList().stream()
                        .map(e -> e.getId())
                        .collect(Collectors.toSet());
                
                // Update local cache
                searchCache.put(normalizedName, result);
                
                // If no results found, add to bloom filter
                if (result.isEmpty()) {
                    nonExistentHeroFilter.put(normalizedName);
                    logger.debug("Added non-existent hero name {} to bloom filter", normalizedName);
                }
                
                return result;
            } catch (Exception e) {
                logger.error("Error searching for hero: {}", name, e);
                return null; // Will be cached as null
            }
        } finally {
            lock.unlock();
            // Only remove the lock and cache if no one is waiting for it
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
        // Check local cache first
        Hero cachedHero = heroCache.get(id);
        if (cachedHero != null) {
            return cachedHero;
        }

        ReentrantLock lock = heroLocks.computeIfAbsent(id, k -> new ReentrantLock());
        
        try {
            lock.lock();
            try {
                // Double-check cache after acquiring lock
                cachedHero = heroCache.get(id);
                if (cachedHero != null) {
                    return cachedHero;
                }

                // Register the hero for monitoring
                cacheUpdateScheduleService.addHeroToMonitor(id);
                Hero hero = externalAPIService.getHero(id);

                if (hero != null) {
                    // Update local cache
                    heroCache.put(id, hero);
                    // Notify subscribers about the initial data
                    notificationService.notifyHeroUpdate(id, hero, UpdateType.NEW);
                }

                return hero;
            } catch (Exception e) {
                logger.error("Error searching for hero: {}", id, e);
                return null; // Will be cached as null
            }
        } finally {
            lock.unlock();
            // Only remove the lock and cache if no one is waiting for it
            if (!lock.hasQueuedThreads()) {
                heroLocks.remove(id);
                heroCache.remove(id);
            }
        }
    }
} 