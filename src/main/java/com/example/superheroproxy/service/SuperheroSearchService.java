package com.example.superheroproxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Biography;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.Image;
import com.example.superheroproxy.proto.PowerStats;
import com.example.superheroproxy.proto.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SuperheroSearchService {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroSearchService.class);

    @Value("${superhero.api.token}")
    private String apiToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SuperheroSearchService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    // Public setter for testing
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    @Cacheable(value = "superheroCache", key = "#name.toLowerCase()")
    public SearchResponse searchHero(String name) {
        try {
            return searchHeroInternal(name);
        } catch (Exception e) {
            logger.error("Error searching for hero: {}", name, e);
            throw new RuntimeException("Failed to search for hero: " + name, e);
        }
    }

    private SearchResponse searchHeroInternal(String name) throws Exception {
        logger.info("Cache miss for hero: {}", name);
        String token = apiToken.trim();
        String url = String.format("https://superheroapi.com/api/%s/search/%s", token, name);
        logger.debug("Making request to URL: {}", url);
        
        String jsonResponse = restTemplate.getForObject(url, String.class);
        logger.debug("API Response: {}", jsonResponse);
        
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        SearchResponse.Builder responseBuilder = SearchResponse.newBuilder()
            .setResponse("success")
            .setResultsFor(name);

        if (rootNode.has("results") && rootNode.get("results").isArray()) {
            logger.debug("Number of results: {}", rootNode.get("results").size());
            for (JsonNode heroNode : rootNode.get("results")) {
                Hero.Builder heroBuilder = Hero.newBuilder()
                    .setId(heroNode.get("id").asText())
                    .setName(heroNode.get("name").asText());

                // Set power stats
                JsonNode powerStatsNode = heroNode.get("powerstats");
                PowerStats.Builder powerStatsBuilder = PowerStats.newBuilder()
                    .setIntelligence(powerStatsNode.get("intelligence").asText())
                    .setStrength(powerStatsNode.get("strength").asText())
                    .setSpeed(powerStatsNode.get("speed").asText());
                heroBuilder.setPowerstats(powerStatsBuilder);

                // Set biography
                JsonNode biographyNode = heroNode.get("biography");
                Biography.Builder biographyBuilder = Biography.newBuilder()
                    .setFullName(biographyNode.get("full-name").asText())
                    .setPublisher(biographyNode.get("publisher").asText());
                heroBuilder.setBiography(biographyBuilder);

                // Set image
                JsonNode imageNode = heroNode.get("image");
                Image.Builder imageBuilder = Image.newBuilder()
                    .setUrl(imageNode.get("url").asText());
                heroBuilder.setImage(imageBuilder);

                responseBuilder.addResults(heroBuilder);
            }
        } else {
            logger.warn("No results found in response");
        }
        
        return responseBuilder.build();
    }
} 