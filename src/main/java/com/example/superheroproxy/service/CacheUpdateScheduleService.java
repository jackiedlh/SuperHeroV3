package com.example.superheroproxy.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.config.CacheConfig;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.UpdateType;
import com.example.superheroproxy.utils.SuperheroIdStore;
import com.example.superheroproxy.config.AsyncConfig;

/**
 * Service responsible for managing and updating the superhero data cache.
 * This service:
 * - Monitors specific heroes for updates
 * - Periodically checks for changes in hero data
 * - Updates the cache when changes are detected
 * - Notifies subscribers about data updates
 * 
 * The service uses Spring's scheduling mechanism to periodically check
 * for updates and maintains a thread-safe set of monitored heroes.
 */
@Service
@EnableAsync
public class CacheUpdateScheduleService {
    private static final Logger logger = LoggerFactory.getLogger(CacheUpdateScheduleService.class);
    private static final Pattern HERO_ID_PATTERN = Pattern.compile("\\|\\s*(\\d+)\\s*\\|");

    // Update interval in seconds, injected from application properties
    @Value("${superhero.cache.update.interval}")
    private long updateIntervalSeconds;

    @Value("${superhero.cache.batch.size:100}")
    private int batchSize;

    // URL for fetching hero IDs, injected from application properties
    @Value("${superhero.api.ids.url}")
    private String heroIdsUrl;

    private final CacheManager cacheManager;
    //MOCK for local monitored heros, need check hero status
    private final Set<String> monitoredHeroes;
    private final NotificationService notificationService;
    private final ExternalApiService externalAPIService;
    private final RestTemplate restTemplate;
    private final AsyncConfig asyncConfig;

    /**
     * Constructs a new CacheUpdateScheduleService with the required dependencies.
     * 
     * @param cacheManager The Spring CacheManager for managing caches
     * @param notificationService Service for notifying about hero updates
     * @param externalAPIService Service for interacting with the external API
     * @param restTemplate The RestTemplate for making HTTP requests
     * @param asyncConfig The AsyncConfig for configuring asynchronous execution
     */
    public CacheUpdateScheduleService(
            CacheManager cacheManager,
            NotificationService notificationService,
            ExternalApiService externalAPIService,
            RestTemplate restTemplate,
            AsyncConfig asyncConfig) {
        this.cacheManager = cacheManager;
        this.monitoredHeroes = new ConcurrentSkipListSet<>();
        this.notificationService = notificationService;
        this.externalAPIService = externalAPIService;
        this.restTemplate = restTemplate;
        this.asyncConfig = asyncConfig;
    }

    /**
     * Adds a hero to the list of monitored heroes.
     * Monitored heroes will be checked periodically for updates.
     * 
     * @param heroId The ID of the hero to monitor
     */
    public void addHeroToMonitor(String heroId) {
        monitoredHeroes.add(heroId);
    }

    /**
     * Periodically checks for updates to monitored heroes.
     * This scheduled method:
     * 1. Retrieves current hero data from the external API
     * 2. Compares with cached data
     * 3. Updates the cache if changes are detected
     * 4. Notifies subscribers about updates
     * 
     * The method runs at the interval specified by updateIntervalSeconds.
     */
    @Scheduled(fixedRateString = "${superhero.cache.update.interval}", timeUnit = TimeUnit.SECONDS)
    public void checkForUpdates() {
        logger.info("Starting cache update check for {} monitored heroes", monitoredHeroes.size());

        // First check for new heroes
        mockGetNewAndUpdatedHeroes();

        Cache cache = cacheManager.getCache(CacheConfig.SUPERHERO_CACHE);
        if (cache == null) {
            logger.error("Cache 'superheroCache' not found");
            return;
        }

        // Process all monitored heroes asynchronously
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String heroId : monitoredHeroes) {
            try {
                futures.add(processHeroUpdate(heroId, cache));
            } catch (Exception e) {
                logger.error("Error processing hero: {}", heroId, e);
                monitoredHeroes.remove(heroId);
            }
        }

        // Wait for all updates to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .exceptionally(ex -> {
                logger.error("Error during hero updates: {}", ex.getMessage());
                return null;
            });

        // Cleanup: Remove heroes from monitoring if they're not in cache
        cleanupMonitoredHeroes();
    }

    @Async
    public CompletableFuture<Void> processHeroUpdate(String heroId, Cache cache) {
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
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Error processing hero update for {}: {}", heroId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    //Mock for update hero //TODO: remove it for release
    private boolean foreUpdate(Hero newHero) {
        return Integer.parseInt(newHero.getId()) % 10 == 0;
    }

    private void mockGetNewAndUpdatedHeroes() {
        try {
            String htmlContent = restTemplate.getForObject(heroIdsUrl, String.class);
            Map<String, String> superheroIds = SuperheroIdStore.getSuperheroIds(htmlContent);

            //for local server performance, only get first 20 heroes, remove first and add 2 more for next //TODO: MOCK only
            int size = monitoredHeroes.isEmpty()? 20: Math.min(monitoredHeroes.size()+2, superheroIds.size());

            superheroIds.keySet().stream().limit(size).forEach(heroId -> {
                if (!monitoredHeroes.contains(heroId)) {
                    logger.info("Found new hero ID: {}", heroId);
                    addHeroToMonitor(heroId);
                }
            });

        } catch (Exception e) {
            logger.error("Error checking for new heroes", e);
        }
    }

    private void cleanupMonitoredHeroes() {
        Cache cache = cacheManager.getCache(CacheConfig.SUPERHERO_CACHE);
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