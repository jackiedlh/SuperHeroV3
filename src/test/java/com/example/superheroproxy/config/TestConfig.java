package com.example.superheroproxy.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.mockito.Mockito;
import net.devh.boot.grpc.server.config.GrpcServerProperties;

@TestConfiguration
public class TestConfig {

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager("superheroCache");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public GrpcServerProperties grpcServerProperties() {
        GrpcServerProperties properties = new GrpcServerProperties();
        properties.setPort(9090);
        return properties;
    }
} 