package com.example.superheroproxy.service;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.UpdateType;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

@Service
public class CacheUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(CacheUpdateService.class);
    private static final Pattern HERO_ID_PATTERN = Pattern.compile("\\|\\s*(\\d+)\\s*\\|");

    @Value("${superhero.cache.update.interval:3600}")
    private long updateIntervalSeconds;

    @Value("${superhero.api.ids.url:https://superheroapi.com/ids.html}")
    private String heroIdsUrl;

    private final CacheManager cacheManager;
    private final Set<String> monitoredHeroes;
    private final NotificationService notificationService;
    private final ExternalApiService externalAPIService;
    private final RestTemplate restTemplate;

    public CacheUpdateService(
            CacheManager cacheManager,
            NotificationService notificationService,
            ExternalApiService externalAPIService,
            RestTemplate restTemplate) {
        this.cacheManager = cacheManager;
        this.monitoredHeroes = new ConcurrentSkipListSet<>();
        this.notificationService = notificationService;
        this.externalAPIService = externalAPIService;
        this.restTemplate = restTemplate;
    }

    public void addHeroToMonitor(String heroId) {
        monitoredHeroes.add(heroId);
    }

    @Scheduled(fixedRateString = "${superhero.cache.update.interval:3600}", timeUnit = TimeUnit.SECONDS)
    public void updateCache() {
        logger.info("Starting scheduled cache update for {} heroes", monitoredHeroes.size());
        
        // First check for new heroes
        checkForNewHeroes();
        
        Cache cache = cacheManager.getCache("superheroCache");
        if (cache == null) {
            logger.error("Cache 'superheroCache' not found");
            return;
        }

        // Create a copy of monitoredHeroes to avoid concurrent modification
        for (String heroId : monitoredHeroes) {
            try {
                // Get the current cached value
                Hero cachedHero = cache.get(heroId, Hero.class);

                // Get new hero data from external API
                Hero newHero = externalAPIService.getHero(heroId);

                // If cached value exists and is different from new value, update the cache
                if (cachedHero == null) {
                    cache.put(heroId, newHero);
                    logger.info("Added new hero to cache: {}", heroId);
                    notificationService.notifyHeroUpdate(heroId, newHero, UpdateType.NEW);
                } else if (!cachedHero.equals(newHero)) {
                    cache.put(heroId, newHero);
                    logger.info("Updated cache for hero: {}", heroId);
                    notificationService.notifyHeroUpdate(heroId, newHero, UpdateType.UPDATED);
                } else {
                    logger.debug("No changes detected for hero: {}", heroId);
                }
            } catch (Exception e) {
                logger.error("Error updating cache for hero: {}", heroId, e);
                // If there's an error getting the hero, remove it from monitoring
                monitoredHeroes.remove(heroId);
                logger.info("Removed hero from monitoring due to error: {}", heroId);
            }
        }

        // Cleanup: Remove heroes from monitoring if they're not in cache
        cleanupMonitoredHeroes();
    }

    private void checkForNewHeroes() {
        try {
            String htmlContent = restTemplate.getForObject(heroIdsUrl, String.class);
            if (htmlContent != null) {
                Matcher matcher = HERO_ID_PATTERN.matcher(htmlContent);
                while (matcher.find()) {
                    String heroId = matcher.group(1);
                    if (!monitoredHeroes.contains(heroId)) {
                        logger.info("Found new hero ID: {}", heroId);
                        addHeroToMonitor(heroId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error checking for new heroes", e);
        }
    }

    private void cleanupMonitoredHeroes() {
        Cache cache = cacheManager.getCache("superheroCache");
        if (cache == null) {
            logger.error("Cache 'superheroCache' not found during cleanup");
            return;
        }

        // Create a copy of monitoredHeroes to avoid concurrent modification
        for (String heroId : new ConcurrentSkipListSet<>(monitoredHeroes)) {
            Cache.ValueWrapper value = cache.get(heroId);
            if (value == null) {
                // Hero is not in cache, remove from monitoring
                monitoredHeroes.remove(heroId);
                logger.info("Removed hero from monitoring (cache evicted): {}", heroId);
            }
        }
    }
} 