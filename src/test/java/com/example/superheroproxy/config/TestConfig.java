package com.example.superheroproxy.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestConfig {

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager("superheroCache");
    }
} 