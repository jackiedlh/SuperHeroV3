package com.example.superheroproxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Biography;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.Image;
import com.example.superheroproxy.proto.PowerStats;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class SuperheroServiceImpl extends SuperheroServiceGrpc.SuperheroServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(SuperheroServiceImpl.class);

    @Value("${superhero.api.token}")
    private String apiToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SuperheroServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    // Package-private setter for testing
    void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    @Override
    public void searchHero(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        try {
            String token = apiToken.trim();
            logger.debug("Raw API token: '{}'", apiToken);
            logger.debug("Trimmed API token: '{}'", token);
            String url = String.format("https://superheroapi.com/api/%s/search/%s", token, request.getName());
            logger.debug("Making request to URL: {}", url);
            String jsonResponse = restTemplate.getForObject(url, String.class);
            logger.debug("API Response: {}", jsonResponse);
            
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            SearchResponse.Builder responseBuilder = SearchResponse.newBuilder()
                .setResponse("success")
                .setResultsFor(request.getName());

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
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error processing request", e);
            responseObserver.onError(e);
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
} 