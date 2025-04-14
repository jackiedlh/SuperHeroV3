package com.example.superheroproxy.controller;

import com.example.superheroproxy.config.CacheConfig;
import com.example.superheroproxy.dto.HeroDto;
import com.example.superheroproxy.dto.SearchResultDto;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.service.SuperheroProxyService;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST Controller for managing superhero-related operations.
 * Provides endpoints for cache management and hero search functionality.
 */
@RestController
public class HeroController {

    private final CacheManager cacheManager;
    private final SuperheroProxyService superheroProxyService;

    /**
     * Constructor for HeroController.
     * 
     * @param cacheManager The cache manager for handling superhero cache operations
     * @param superheroProxyService The service for superhero-related operations
     */
    @Autowired
    public HeroController(CacheManager cacheManager, SuperheroProxyService superheroProxyService) {
        this.cacheManager = cacheManager;
        this.superheroProxyService = superheroProxyService;
    }


    /**
     * Searches for heroes by name using gRPC service.
     *
     * @param name The name to search for
     * @return ResponseEntity containing a list of search results or 400 if the request fails
     */
    @GetMapping("/api/search")
    public ResponseEntity<List<SearchResultDto>> searchHero(@RequestParam String name) {
        try {
            // Create a latch to wait for the asynchronous response
            CountDownLatch latch = new CountDownLatch(1);
            SearchResponse[] responseHolder = new SearchResponse[1];
            Exception[] errorHolder = new Exception[1];

            SearchRequest request = SearchRequest.newBuilder()
                    .setName(name)
                    .build();

            // Create a StreamObserver to handle the gRPC response
            superheroProxyService.searchHero(request, new StreamObserver<SearchResponse>() {
                @Override
                public void onNext(SearchResponse response) {
                    responseHolder[0] = response;
                }

                @Override
                public void onError(Throwable t) {
                    errorHolder[0] = new Exception(t);
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            // Wait for the response with a timeout
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return ResponseEntity.badRequest().build();
            }

            if (errorHolder[0] != null) {
                throw errorHolder[0];
            }

            SearchResponse response = responseHolder[0];
            if (response == null) {
                return ResponseEntity.badRequest().build();
            }

            // Convert the response to DTOs
            List<SearchResultDto> results = response.getResultsList().stream()
                    .map(hero -> new SearchResultDto(hero.getId(), hero.getName()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retrieves all keys currently stored in the superhero cache.
     * 
     * @return ResponseEntity containing a set of cache keys or 404 if cache is not found
     */
    @GetMapping("/api/cache/keys")
    public ResponseEntity<Set<String>> getCacheKeys() {
        Cache cache = cacheManager.getCache(CacheConfig.SUPERHERO_CACHE);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        // Get the native cache to access keys
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
            (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        
        Set<String> keys = nativeCache.asMap().keySet().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());

        return ResponseEntity.ok(keys);
    }

    /**
     * Retrieves a specific hero from the cache by ID.
     * 
     * @param heroId The ID of the hero to retrieve
     * @return ResponseEntity containing the hero data or 404 if not found
     */
    @GetMapping("/api/cache/{heroId}")
    public ResponseEntity<HeroDto> getHeroFromCache(@PathVariable String heroId) {
        Cache cache = cacheManager.getCache(CacheConfig.SUPERHERO_CACHE);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        Cache.ValueWrapper valueWrapper = cache.get(heroId);
        if (valueWrapper == null) {
            return ResponseEntity.notFound().build();
        }

        Hero hero = (Hero) valueWrapper.get();
        return ResponseEntity.ok(new HeroDto(hero.getId(), hero.getName()));
    }

    /**
     * Updates the name of a hero in the cache.
     * 
     * @param heroId The ID of the hero to update
     * @param request Map containing the new name under the "name" key
     * @return ResponseEntity containing the updated hero data or 404 if not found
     */
    @PostMapping("/api/cache/{heroId}/name")
    public ResponseEntity<HeroDto> updateHeroNameInCache(@PathVariable String heroId, @RequestBody Map<String, String> request) {
        Cache cache = cacheManager.getCache("superheroCache");
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        Cache.ValueWrapper valueWrapper = cache.get(heroId);
        if (valueWrapper == null) {
            return ResponseEntity.notFound().build();
        }

        Hero hero = (Hero) valueWrapper.get();
        Hero updatedHero = Hero.newBuilder(hero)
                .setName(request.get("name"))
                .build();

        cache.put(heroId, updatedHero);
        return ResponseEntity.ok(new HeroDto(updatedHero.getId(), updatedHero.getName()));
    }

    /**
     * Retrieves statistics about the superhero cache.
     * 
     * @return ResponseEntity containing cache statistics or 404 if cache is not found
     */
    @GetMapping("/api/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Cache cache = cacheManager.getCache("superheroCache");
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
            (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", nativeCache.estimatedSize());
        stats.put("hitCount", nativeCache.stats().hitCount());
        stats.put("missCount", nativeCache.stats().missCount());
        stats.put("hitRate", nativeCache.stats().hitRate());
        stats.put("evictionCount", nativeCache.stats().evictionCount());
        
        return ResponseEntity.ok(stats);
    }


} 