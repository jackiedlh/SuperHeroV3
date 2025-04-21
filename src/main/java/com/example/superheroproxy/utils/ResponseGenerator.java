package com.example.superheroproxy.utils;

import java.util.stream.StreamSupport;

import com.example.superheroproxy.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResponseGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ResponseGenerator.class);

    static ObjectMapper objectMapper = new ObjectMapper();

    public static SearchResponse createSearchResponse(String searchName, String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
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

    public static Hero generateHero(String jsonResponse) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        return generateHero(rootNode);
    }

    public static Hero generateHero(JsonNode heroNode) {
        if (heroNode == null) {
            throw new IllegalArgumentException("Hero node cannot be null");
        }

        Hero.Builder heroBuilder = Hero.newBuilder()
                .setId(heroNode.get("id").asText())
                .setName(heroNode.get("name").asText());

        // Set power stats
        JsonNode powerStatsNode = heroNode.get("powerstats");
        if (powerStatsNode != null) {
            PowerStats.Builder powerStatsBuilder = PowerStats.newBuilder();
            setIfPresent(powerStatsNode, "intelligence", powerStatsBuilder::setIntelligence);
            setIfPresent(powerStatsNode, "strength", powerStatsBuilder::setStrength);
            setIfPresent(powerStatsNode, "speed", powerStatsBuilder::setSpeed);
            setIfPresent(powerStatsNode, "durability", powerStatsBuilder::setDurability);
            setIfPresent(powerStatsNode, "power", powerStatsBuilder::setPower);
            setIfPresent(powerStatsNode, "combat", powerStatsBuilder::setCombat);
            heroBuilder.setPowerstats(powerStatsBuilder);
        }

        // Set biography
        JsonNode biographyNode = heroNode.get("biography");
        if (biographyNode != null) {
            Biography.Builder biographyBuilder = Biography.newBuilder();
            setIfPresent(biographyNode, "full-name", biographyBuilder::setFullName);
            setIfPresent(biographyNode, "publisher", biographyBuilder::setPublisher);
            setIfPresent(biographyNode, "alter-egos", biographyBuilder::setAlterEgos);
            setIfPresent(biographyNode, "place-of-birth", biographyBuilder::setPlaceOfBirth);
            setIfPresent(biographyNode, "first-appearance", biographyBuilder::setFirstAppearance);
            setIfPresent(biographyNode, "alignment", biographyBuilder::setAlignment);
            heroBuilder.setBiography(biographyBuilder);
        }

        // Set appearance
        JsonNode appearanceNode = heroNode.get("appearance");
        if (appearanceNode != null) {
            com.example.superheroproxy.proto.Appearance.Builder appearanceBuilder = com.example.superheroproxy.proto.Appearance.newBuilder();
            setIfPresent(appearanceNode, "gender", appearanceBuilder::setGender);
            setIfPresent(appearanceNode, "race", appearanceBuilder::setRace);
            setIfPresent(appearanceNode, "eye-color", appearanceBuilder::setEyeColor);
            setIfPresent(appearanceNode, "hair-color", appearanceBuilder::setHairColor);
            heroBuilder.setAppearance(appearanceBuilder);
        }

        // Set work
        JsonNode workNode = heroNode.get("work");
        if (workNode != null) {
            com.example.superheroproxy.proto.Work.Builder workBuilder = com.example.superheroproxy.proto.Work.newBuilder();
            setIfPresent(workNode, "occupation", workBuilder::setOccupation);
            setIfPresent(workNode, "base", workBuilder::setBase);
            heroBuilder.setWork(workBuilder);
        }

        // Set connections
        JsonNode connectionsNode = heroNode.get("connections");
        if (connectionsNode != null) {
            com.example.superheroproxy.proto.Connections.Builder connectionsBuilder = com.example.superheroproxy.proto.Connections.newBuilder();
            setIfPresent(connectionsNode, "group-affiliation", connectionsBuilder::setGroupAffiliation);
            setIfPresent(connectionsNode, "relatives", connectionsBuilder::setRelatives);
            heroBuilder.setConnections(connectionsBuilder);
        }

        // Set image
        JsonNode imageNode = heroNode.get("image");
        if (imageNode != null) {
            Image.Builder imageBuilder = Image.newBuilder();
            setIfPresent(imageNode, "url", imageBuilder::setUrl);
            heroBuilder.setImage(imageBuilder);
        }

        return heroBuilder.build();
    }

    private static void setIfPresent(JsonNode node, String fieldName, java.util.function.Consumer<String> setter) {
        JsonNode value = node.get(fieldName);
        if (value != null && !value.isNull()) {
            setter.accept(value.asText());
        }
    }
}
