package com.example.superheroproxy.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import com.example.superheroproxy.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.UpdateType;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * The core service that handles superhero data operations and caching.
 * This service:
 * - Manages the caching of hero search results and hero details
 * - Coordinates with the external API service for data retrieval
 * - Monitors hero data for updates
 * - Notifies subscribers about hero changes
 * - Implements cache penetration protection
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
    
    // 用于缓存空结果的缓存
    private final Cache<String, Boolean> emptyResultCache;
    // 用于请求合并的缓存
    private final ConcurrentHashMap<String, Object> requestLocks;

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
        
        // 初始化空结果缓存，设置5分钟过期时间
        this.emptyResultCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
                
        this.requestLocks = new ConcurrentHashMap<>();
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
        // 检查空结果缓存
        if (emptyResultCache.getIfPresent(name.toLowerCase()) != null) {
            logger.debug("Empty result found in cache for name: {}", name);
            return Set.of();
        }

        try {
            SearchResponse searchResponse = externalAPIService.searchHero(name);
            Set<String> ids = searchResponse.getResultsList().stream()
                    .map(e -> e.getId())
                    .collect(Collectors.toSet());

            // 如果结果为空，将其加入空结果缓存
            if (ids.isEmpty()) {
                emptyResultCache.put(name.toLowerCase(), true);
            }

            return ids;
        } catch (Exception e) {
            logger.error("Error searching for hero: {}", name, e);
            throw new RuntimeException("Error searching for hero", e);
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
        // 检查空结果缓存
        if (emptyResultCache.getIfPresent(id) != null) {
            logger.debug("Empty result found in cache for id: {}", id);
            return null;
        }

        // 获取或创建请求锁
        Object lock = requestLocks.computeIfAbsent(id, k -> new Object());
        
        synchronized (lock) {
            try {
                // 再次检查缓存，防止重复请求
                if (emptyResultCache.getIfPresent(id) != null) {
                    return null;
                }

                // Register the hero for monitoring
                cacheUpdateScheduleService.addHeroToMonitor(id);
                Hero hero = externalAPIService.getHero(id);

                // 如果英雄不存在，加入空结果缓存
                if (hero == null) {
                    emptyResultCache.put(id, true);
                } else {
                    // Notify subscribers about the initial data
                    notificationService.notifyHeroUpdate(id, hero, UpdateType.NEW);
                }

                return hero;
            } catch (Exception e) {
                logger.error("Error searching for hero: {}", id, e);
                throw new RuntimeException("Failed to search for hero: " + id, e);
            } finally {
                // 清理请求锁
                requestLocks.remove(id);
            }
        }
    }
} 