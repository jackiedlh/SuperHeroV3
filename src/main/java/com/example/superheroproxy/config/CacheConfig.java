package com.example.superheroproxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SUPERHERO_CACHE = "superheroCache";
    public static final String HERO_SEARCH_CACHE = "heroSearchCache";

    @Value("${spring.cache.caffeine.spec}")
    private String caffeineSpec;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SUPERHERO_CACHE, HERO_SEARCH_CACHE);
        cacheManager.setCaffeine(Caffeine.from(caffeineSpec).recordStats());
        return cacheManager;
    }
} 