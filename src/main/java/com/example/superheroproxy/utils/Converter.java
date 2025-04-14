package com.example.superheroproxy.utils;

import com.example.superheroproxy.proto.Biography;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.PowerStats;

import java.util.HashMap;
import java.util.Map;

public class Converter {

    private Converter() {}

    public static Map<String, Object> convertHeroUpdateToMap(HeroUpdate update) {
        Map<String, Object> result = new HashMap<>();
        result.put("heroId", update.getHeroId());
        result.put("hero", convertHeroToMap(update.getHero()));
        result.put("updateType", update.getUpdateType().name());
        return result;
    }

    public static  Map<String, Object> convertHeroToMap(Hero hero) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", hero.getId());
        result.put("name", hero.getName());
        result.put("powerstats", convertPowerStatsToMap(hero.getPowerstats()));
        result.put("biography", convertBiographyToMap(hero.getBiography()));
        return result;
    }

    public static  Map<String, Object> convertPowerStatsToMap(PowerStats powerStats) {
        Map<String, Object> result = new HashMap<>();
        result.put("intelligence", powerStats.getIntelligence());
        result.put("strength", powerStats.getStrength());
        result.put("speed", powerStats.getSpeed());
        result.put("durability", powerStats.getDurability());
        result.put("power", powerStats.getPower());
        result.put("combat", powerStats.getCombat());
        return result;
    }

    public static  Map<String, Object> convertBiographyToMap(Biography biography) {
        Map<String, Object> result = new HashMap<>();
        result.put("fullName", biography.getFullName());
        result.put("alterEgos", biography.getAlterEgos());
        result.put("aliases", biography.getAliasesList());
        result.put("placeOfBirth", biography.getPlaceOfBirth());
        result.put("firstAppearance", biography.getFirstAppearance());
        result.put("publisher", biography.getPublisher());
        result.put("alignment", biography.getAlignment());
        return result;
    }
}
