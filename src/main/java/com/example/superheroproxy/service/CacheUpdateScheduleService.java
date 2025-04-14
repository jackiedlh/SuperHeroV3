package com.example.superheroproxy.service;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;

import com.example.superheroproxy.utils.SuperheroIdParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.UpdateType;

@Service
public class CacheUpdateScheduleService {
    private static final Logger logger = LoggerFactory.getLogger(CacheUpdateScheduleService.class);
    private static final Pattern HERO_ID_PATTERN = Pattern.compile("\\|\\s*(\\d+)\\s*\\|");

    @Value("${superhero.cache.update.interval}")
    private long updateIntervalSeconds;

    @Value("${superhero.api.ids.url}")
    private String heroIdsUrl;

    private final CacheManager cacheManager;
    private final Set<String> monitoredHeroes;
    private final NotificationService notificationService;
    private final ExternalApiService externalAPIService;
    private final RestTemplate restTemplate;

    public CacheUpdateScheduleService(
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

    @Scheduled(fixedRateString = "${superhero.cache.update.interval}", timeUnit = TimeUnit.SECONDS)
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
                } else if (!cachedHero.equals(newHero) || foreUpdate(newHero)) {
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

    //Mock for update hero //TODO: remove it for release
    private boolean foreUpdate(Hero newHero) {
        return Math.random() < 0.3 || Integer.parseInt(newHero.getId())<=5;
    }

    private void checkForNewHeroes() {
        try {
            String htmlContent = restTemplate.getForObject(heroIdsUrl, String.class);
            Map<String, String> superheroIds = SuperheroIdParser.parseSuperheroIds(htmlContent);



            //for local server performance, only get first 20 heroes, and add 2 more for next //TODO: remove for release
            int size = monitoredHeroes.isEmpty()? 20: Math.min(monitoredHeroes.size()+2, superheroIds.size());

            superheroIds.keySet().stream().limit(size).forEach(heroId -> {
                if (!monitoredHeroes.contains(heroId)) {
                    logger.info("Found new hero ID: {}", heroId);
                    addHeroToMonitor(heroId);
                }
            });

//            superheroIds.keySet().forEach(heroId ->{
//                if (!monitoredHeroes.contains(heroId)) {
//                    logger.info("Found new hero ID: {}", heroId);
//                    addHeroToMonitor(heroId);
//                }
//            });


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