package com.example.superheroproxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;

@Service
public class CacheStatService {

    private static final Logger logger = LoggerFactory.getLogger(CacheStatService.class);
    private final CacheManager cacheManager;

    public CacheStatService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public CacheStats getCacheStats() {
        Cache cache = cacheManager.getCache("superheroCache");
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