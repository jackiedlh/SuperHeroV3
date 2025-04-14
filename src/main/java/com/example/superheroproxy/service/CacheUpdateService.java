package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

@Service
public class CacheUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(CacheUpdateService.class);

    @Value("${superhero.cache.update.interval:3600}")
    private long updateIntervalSeconds;

    private final CacheManager cacheManager;
    private final Set<String> monitoredHeroes;
    private final NotificationService notificationService;
    private final ExternalApiService externalAPIService;

    public CacheUpdateService(
            CacheManager cacheManager,
            NotificationService notificationService,
            ExternalApiService externalAPIService) {
        this.cacheManager = cacheManager;
        this.monitoredHeroes = new ConcurrentSkipListSet<>();
        this.notificationService = notificationService;
        this.externalAPIService = externalAPIService;
    }

    public void addHeroToMonitor(String heroName) {
        monitoredHeroes.add(heroName.toLowerCase());
    }

    @Scheduled(fixedRateString = "${superhero.cache.update.interval:3600}", timeUnit = TimeUnit.SECONDS)
    public void updateCache() {
        logger.info("Starting scheduled cache update for {} heroes", monitoredHeroes.size());
        
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
                if (cachedHero == null || !cachedHero.equals(newHero)) {
                    cache.put(heroId, newHero);
                    logger.info("Updated cache for hero: {}", heroId);
                    
                    // Notify subscribers about the update
                    notificationService.notifyHeroUpdate(heroId, newHero);
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