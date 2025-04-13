package com.example.superheroproxy.utils;

import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.superheroproxy.proto.Biography;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.Image;
import com.example.superheroproxy.proto.PowerStats;
import com.example.superheroproxy.proto.SearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResponseGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ResponseGenerator.class);

    static ObjectMapper objectMapper = new ObjectMapper();

    public static SearchResponse createSearchResponse(String searchName, String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode results = root.get("results");

            SearchResponse.Builder responseBuilder = SearchResponse.newBuilder()
                .setResponse("success")
                .setResultsFor(searchName);

            if (results != null && results.isArray()) {
                StreamSupport.stream(results.spliterator(), false)
                    .map(ResponseGenerator::generateHero)
                    .forEach(responseBuilder::addResults);
            }

            logger.info("Found {} exact matches for hero: {}", responseBuilder.getResultsCount(), searchName);
            return responseBuilder.build();
        } catch (Exception e) {
            logger.error("Error creating search response", e);
            throw new RuntimeException("Error creating search response", e);
        }
    }

    public static Hero generateHero(String jsonResponse)  throws JsonProcessingException{
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        return generateHero(rootNode);
    }

    public static Hero generateHero(JsonNode heroNode) {
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
        return heroBuilder.build();
    }
}
