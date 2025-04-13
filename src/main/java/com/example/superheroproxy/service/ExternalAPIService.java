package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ExternalAPIService {
    private static final Logger logger = LoggerFactory.getLogger(ExternalAPIService.class);


    @Value("${superhero.api.token}")
    private String apiToken;

    @Value("${superhero.api.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public ExternalAPIService(RestTemplate restTemplate){
        this.restTemplate = restTemplate;
    }

    public Hero getHero(String id) throws Exception {

        String url = String.format("%s/%s/%s", baseUrl.trim(), apiToken.trim(), id);
        logger.debug("Making request to URL: {}", url);

        String jsonResponse = restTemplate.getForObject(url, String.class);
        logger.debug("API Response: {}", jsonResponse);

        return ResponseGenerator.generateHero(jsonResponse);
    }

    public SearchResponse searchHero(String name) throws Exception {
        logger.info("Cache miss for hero: {}", name);
        String url = String.format("%s/%s/search/%s", baseUrl.trim(), apiToken.trim(), name);
        logger.debug("Making request to URL: {}", url);

        String jsonResponse = restTemplate.getForObject(url, String.class);
        logger.debug("API Response: {}", jsonResponse);

        return ResponseGenerator.createSearchResponse(name, jsonResponse);
    }
}
