package com.example.superheroproxy.controller;

import com.example.superheroproxy.dto.HeroDto;
import com.example.superheroproxy.dto.SearchResultDto;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.service.SuperheroSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class HeroController {

    private final CacheManager cacheManager;
    private final SuperheroSearchService superheroSearchService;

    @Autowired
    public HeroController(CacheManager cacheManager, SuperheroSearchService superheroSearchService) {
        this.cacheManager = cacheManager;
        this.superheroSearchService = superheroSearchService;
    }

    @GetMapping("/api/cache/keys")
    public ResponseEntity<Set<String>> getCacheKeys() {
        Cache cache = cacheManager.getCache("superheroCache");
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

    @GetMapping("/api/{heroId}")
    public ResponseEntity<HeroDto> getHero(@PathVariable String heroId) {
        Cache cache = cacheManager.getCache("superheroCache");
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

    @PostMapping("/api/{heroId}/name")
    public ResponseEntity<HeroDto> updateHeroName(@PathVariable String heroId, @RequestBody Map<String, String> request) {
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

    @GetMapping("/api/search")
    public ResponseEntity<List<SearchResultDto>> searchHero(@RequestParam String name) {
        try {
            SearchResponse response = superheroSearchService.searchHero(name);
            List<SearchResultDto> results = response.getResultsList().stream()
                .map(hero -> new SearchResultDto(hero.getId(), hero.getName()))
                .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
} 