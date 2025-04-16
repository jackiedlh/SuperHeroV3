package com.example.superheroproxy.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Value("${rate.limit.permits-per-second:10}")
    private double permitsPerSecond;

    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(permitsPerSecond);
    }
} 