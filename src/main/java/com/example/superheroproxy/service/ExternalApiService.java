package com.example.superheroproxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;

/**
 * Service responsible for interacting with the external superhero API.
 * This service:
 * - Makes HTTP requests to the external API
 * - Handles API authentication using tokens
 * - Converts JSON responses to protocol buffer objects
 * - Provides methods for both hero search and individual hero retrieval
 * - Implements circuit breaker pattern for fault tolerance
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

    /**
     * Constructs a new ExternalApiService with the specified RestTemplate.
     * 
     * @param restTemplate The RestTemplate instance for making HTTP requests
     */
    public ExternalApiService(RestTemplate restTemplate){
        this.restTemplate = restTemplate;
    }

    /**
     * Retrieves detailed information about a specific hero by ID from the external API.
     * This method:
     * 1. Constructs the API URL with the hero ID
     * 2. Makes an HTTP GET request to the external API
     * 3. Converts the JSON response to a Hero protocol buffer object
     * 
     * @param id The ID of the hero to retrieve
     * @return A Hero object containing the hero's details
     * @throws Exception if there's an error during the API call or response processing
     */
    @CircuitBreaker(name = "externalApiService", fallbackMethod = "getHeroFallback")
    public Hero getHero(String id) throws Exception {
        String url = String.format("%s/%s/%s", baseUrl.trim(), apiToken.trim(), id);
        logger.debug("Making request to URL: {}", url);

        String jsonResponse = restTemplate.getForObject(url, String.class);
        return ResponseGenerator.generateHero(jsonResponse);
    }

    /**
     * Fallback method for getHero when the circuit breaker is open or there's an error.
     * 
     * @param id The ID of the hero that was requested
     * @param exception The exception that triggered the fallback
     * @return A default Hero object or null
     */
    private Hero getHeroFallback(String id, Exception exception) {
        logger.error("Circuit breaker fallback for getHero with id: {}. Error: {}", id, exception.getMessage());
        if (exception instanceof CallNotPermittedException) {
            logger.warn("Circuit breaker is open for getHero operation");
        }
        return null;
    }

    /**
     * Searches for heroes by name using the external API.
     * This method:
     * 1. Constructs the search API URL with the hero name
     * 2. Makes an HTTP GET request to the external API
     * 3. Converts the JSON response to a SearchResponse protocol buffer object
     * 
     * @param name The name of the hero to search for
     * @return A SearchResponse object containing the search results
     * @throws Exception if there's an error during the API call or response processing
     */
    @CircuitBreaker(name = "externalApiService", fallbackMethod = "searchHeroFallback")
    public SearchResponse searchHero(String name) throws Exception {
        logger.info("Cache miss for hero: {}", name);
        String url = String.format("%s/%s/search/%s", baseUrl.trim(), apiToken.trim(), name);
        logger.debug("Making request to URL: {}", url);

        String jsonResponse = restTemplate.getForObject(url, String.class);
        return ResponseGenerator.createSearchResponse(name, jsonResponse);
    }

    /**
     * Fallback method for searchHero when the circuit breaker is open or there's an error.
     * 
     * @param name The name of the hero that was searched for
     * @param exception The exception that triggered the fallback
     * @return A default SearchResponse object with empty results
     */
    private SearchResponse searchHeroFallback(String name, Exception exception) {
        logger.error("Circuit breaker fallback for searchHero with name: {}. Error: {}", name, exception.getMessage());
        if (exception instanceof CallNotPermittedException) {
            logger.warn("Circuit breaker is open for searchHero operation");
        }
        return SearchResponse.newBuilder()
                .setResultsFor(name)
                .setResponse("error")
                .build();
    }
}
