package com.example.superheroproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "notification")
@Getter
@Setter
public class NotificationConfig {
    private int maxSubscribersPerHero = 1000;
    private int maxTotalSubscribers = 10000;
    private int cleanupIntervalMinutes = 5;
    private int subscriberTimeoutMinutes = 30;
    private int notificationThreadPoolSize = 10;
    private int rateLimitPerMinute = 1000;
} 