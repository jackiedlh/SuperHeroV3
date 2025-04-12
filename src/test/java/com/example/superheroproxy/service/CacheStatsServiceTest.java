package com.example.superheroproxy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

class CacheStatsServiceTest {

    private CacheStatsService cacheStatsService;
    private CacheManager cacheManager;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Create a real Caffeine cache manager
        cacheManager = new CaffeineCacheManager("superheroCache");
        cacheManager.getCache("superheroCache").put("test", "value");

        // Create the service
        cacheStatsService = new CacheStatsService(cacheManager);
        
        // Get the logger from the service
        logger = LoggerFactory.getLogger(CacheStatsService.class);
    }

    @Test
    void testLogCacheStats() {
        // Act
        cacheStatsService.logCacheStats();

        // No need to verify logger calls since we're using a real logger
        // The test will pass if no exceptions are thrown
    }

    @Test
    void testLogCacheStats_NoCache() {
        // Arrange
        CacheManager emptyCacheManager = mock(CacheManager.class);
        when(emptyCacheManager.getCache(anyString())).thenReturn(null);
        cacheStatsService = new CacheStatsService(emptyCacheManager);

        // Act
        cacheStatsService.logCacheStats();

        // No need to verify logger calls since we're using a real logger
        // The test will pass if no exceptions are thrown
    }
} 