package com.example.superheroproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {
    private int maxSubscribersPerHero = 1000;
    private int maxTotalSubscribers = 10000;
    private int cleanupIntervalMinutes = 5;
    private int subscriberTimeoutMinutes = 30;
    private int notificationThreadPoolSize = 10;
    private int rateLimitPerMinute = 1000;

    public int getMaxSubscribersPerHero() {
        return maxSubscribersPerHero;
    }

    public void setMaxSubscribersPerHero(int maxSubscribersPerHero) {
        this.maxSubscribersPerHero = maxSubscribersPerHero;
    }

    public int getMaxTotalSubscribers() {
        return maxTotalSubscribers;
    }

    public void setMaxTotalSubscribers(int maxTotalSubscribers) {
        this.maxTotalSubscribers = maxTotalSubscribers;
    }

    public int getCleanupIntervalMinutes() {
        return cleanupIntervalMinutes;
    }

    public void setCleanupIntervalMinutes(int cleanupIntervalMinutes) {
        this.cleanupIntervalMinutes = cleanupIntervalMinutes;
    }

    public int getSubscriberTimeoutMinutes() {
        return subscriberTimeoutMinutes;
    }

    public void setSubscriberTimeoutMinutes(int subscriberTimeoutMinutes) {
        this.subscriberTimeoutMinutes = subscriberTimeoutMinutes;
    }

    public int getNotificationThreadPoolSize() {
        return notificationThreadPoolSize;
    }

    public void setNotificationThreadPoolSize(int notificationThreadPoolSize) {
        this.notificationThreadPoolSize = notificationThreadPoolSize;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }
} 