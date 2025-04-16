package com.example.superheroproxy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;

import java.util.List;

/**
 * Service responsible for interacting with the external superhero API.
 * This service:
 * - Makes HTTP requests to the external API
 * - Handles API authentication using tokens
 * - Converts JSON responses to protocol buffer objects
 * - Provides methods for both hero search and individual hero retrieval
 * - Implements circuit breaker pattern for resilience
 * 
 * The service uses Spring's RestTemplate for HTTP communication and
 * configuration properties for API endpoint and authentication details.
 */
@Service
public class ExternalApiService {
    private static final Logger logger = LoggerFactory.getLogger(ExternalApiService.class);

    // API authentication token injected from application properties
    @Value("${superhero.api.token}")
    private String apiToken;

    // Base URL for the external API injected from application properties
    @Value("${superhero.api.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final CircuitBreakerFactory circuitBreakerFactory;

    /**
     * Constructs a new ExternalApiService with the specified RestTemplate and CircuitBreakerFactory.
     * 
     * @param restTemplate The RestTemplate instance for making HTTP requests
     * @param circuitBreakerFactory The CircuitBreakerFactory for implementing circuit breaker pattern
     */
    public ExternalApiService(RestTemplate restTemplate, CircuitBreakerFactory circuitBreakerFactory) {
        this.restTemplate = restTemplate;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * Retrieves detailed information about a specific hero by ID from the external API.
     * This method is protected by a circuit breaker pattern.
     * 
     * @param id The ID of the hero to retrieve
     * @return A Hero object containing the hero's details
     * @throws Exception if there's an error during the API call or response processing
     */
    public Hero getHero(String id) throws Exception {
        return circuitBreakerFactory.create("getHero")
            .run(
                () -> {
                    String url = String.format("%s/%s/%s", baseUrl.trim(), apiToken.trim(), id);
                    logger.debug("Making request to URL: {}", url);
                    String jsonResponse = restTemplate.getForObject(url, String.class);
                    try {
                        return ResponseGenerator.generateHero(jsonResponse);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                },
                throwable -> {
                    logger.error("Error getting hero with ID: {}. Falling back to default hero.", id, throwable);
                    return Hero.newBuilder()
                        .setId(id)
                        .setName("Unknown Hero")
                        .build();
                }
            );
    }

    /**
     * Searches for heroes by name using the external API.
     * This method is protected by a circuit breaker pattern.
     * 
     * @param name The name of the hero to search for
     * @return A SearchResponse object containing the search results
     * @throws Exception if there's an error during the API call or response processing
     */
    public SearchResponse searchHero(String name) throws Exception {
        return circuitBreakerFactory.create("searchHero")
            .run(
                () -> {
                    logger.info("Cache miss for hero: {}", name);
                    String url = String.format("%s/%s/search/%s", baseUrl.trim(), apiToken.trim(), name);
                    logger.debug("Making request to URL: {}", url);
                    String jsonResponse = restTemplate.getForObject(url, String.class);
                    return ResponseGenerator.createSearchResponse(name, jsonResponse);
                },
                throwable -> {
                    logger.error("Error searching for hero: {}. Falling back to empty search response.", name, throwable);
                    return SearchResponse.newBuilder()
                        .setResponse("error")
                        .setResultsFor(name)
                        .addAllResults(List.of())
                        .build();
                }
            );
    }
}
