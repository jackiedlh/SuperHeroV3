package com.example.superheroproxy.controller;

import com.example.superheroproxy.config.CacheConfig;
import com.example.superheroproxy.dto.HeroDto;
import com.example.superheroproxy.proto.Hero;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final CacheManager cacheManager;

    /**
     * Constructor for HeroController.
     *
     * @param cacheManager The cache manager for handling superhero cache operations
     */
    @Autowired
    public CacheController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }


    /**
     * Retrieves all keys currently stored in the superhero cache.
     *
     * @return ResponseEntity containing a set of cache keys or 404 if cache is not found
     */
    @GetMapping("/keys")
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
    @GetMapping("/{heroId}")
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
    @PostMapping("/{heroId}/name")
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
     * Deletes a hero from the cache by ID.
     *
     * @param heroId The ID of the hero to delete
     * @return ResponseEntity with 200 if successful, 404 if cache or hero not found
     */
    @DeleteMapping("/{heroId}")
    public ResponseEntity<Void> deleteHeroFromCache(@PathVariable String heroId) {
        Cache cache = cacheManager.getCache("superheroCache");
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        cache.evict(heroId);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves statistics about the superhero cache.
     *
     * @return ResponseEntity containing cache statistics or 404 if cache is not found
     */
    @GetMapping("/stats")
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
