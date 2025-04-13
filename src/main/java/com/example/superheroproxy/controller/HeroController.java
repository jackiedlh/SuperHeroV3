package com.example.superheroproxy.controller;

import com.example.superheroproxy.dto.HeroDto;
import com.example.superheroproxy.proto.Hero;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeroController {

    private final CacheManager cacheManager;

    @Autowired
    public HeroController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @GetMapping("/api/heroes/{heroId}")
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
} 