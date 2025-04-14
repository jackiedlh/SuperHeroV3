package com.example.superheroproxy.service;

import com.example.superheroproxy.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * Service responsible for collecting and reporting cache statistics.
 * This service:
 * - Retrieves statistics from the Caffeine cache implementation
 * - Logs detailed cache performance metrics
 * - Provides cache statistics for monitoring and optimization
 * 
 * The service uses Caffeine's built-in statistics collection to track:
 * - Hit and miss rates
 * - Request counts
 * - Eviction counts
 * - Load penalties
 */
@Service
public class CacheStatService {

    private static final Logger logger = LoggerFactory.getLogger(CacheStatService.class);
    private final CacheManager cacheManager;

    /**
     * Constructs a new CacheStatService with the specified cache manager.
     * 
     * @param cacheManager The Spring CacheManager for accessing cache instances
     */
    public CacheStatService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Retrieves and logs statistics for the superhero cache.
     * This method:
     * 1. Gets the cache instance
     * 2. Retrieves statistics if using Caffeine implementation
     * 3. Logs detailed performance metrics
     * 
     * @return CacheStats object containing the statistics, or null if not using Caffeine
     */
    public CacheStats getCacheStats() {
        Cache cache = cacheManager.getCache(CacheConfig.SUPERHERO_CACHE);
        if (cache != null && cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = 
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
            
            CacheStats stats = caffeineCache.stats();
            logger.info("Cache Statistics:");
            logger.info("  Hit Count: {}", stats.hitCount());
            logger.info("  Miss Count: {}", stats.missCount());
            logger.info("  Hit Rate: {}%", String.format("%.2f", stats.hitRate() * 100));
            logger.info("  Miss Rate: {}%", String.format("%.2f", stats.missRate() * 100));
            logger.info("  Request Count: {}", stats.requestCount());
            logger.info("  Eviction Count: {}", stats.evictionCount());
            logger.info("  Average Load Penalty: {} ns", stats.averageLoadPenalty());
            return stats;
        }
        return null;
    }
} 