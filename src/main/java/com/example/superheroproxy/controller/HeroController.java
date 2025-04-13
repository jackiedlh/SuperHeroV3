package com.example.superheroproxy.controller;

import com.example.superheroproxy.dto.HeroDto;
import com.example.superheroproxy.proto.Hero;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class HeroController {

    private final CacheManager cacheManager;

    @Autowired
    public HeroController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
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
} 