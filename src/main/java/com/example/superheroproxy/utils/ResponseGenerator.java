package com.example.superheroproxy.utils;

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

    public static SearchResponse createSearchResponse(String name, String jsonResponse) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        return createSearchResponse(name, rootNode);
    }

    public static SearchResponse createSearchResponse(String name, JsonNode rootNode) {
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
