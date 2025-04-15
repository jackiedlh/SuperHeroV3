package com.example.superheroproxy.controller;

import com.example.superheroproxy.config.CacheConfig;
import com.example.superheroproxy.dto.HeroDto;
import com.example.superheroproxy.dto.SearchResultDto;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.service.SuperheroProxyService;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST Controller for managing superhero-related operations.
 * Provides endpoints for cache management and hero search functionality.
 */
@RestController
@RequestMapping("/api/hero")
public class HeroController {

    private final SuperheroProxyService superheroProxyService;

    /**
     * Constructor for HeroController.
     * 
     * @param superheroProxyService The service for superhero-related operations
     */
    @Autowired
    public HeroController( SuperheroProxyService superheroProxyService) {
        this.superheroProxyService = superheroProxyService;
    }


    /**
     * Searches for heroes by name using gRPC service.
     *
     * @param name The name to search for
     * @return ResponseEntity containing a list of search results or 400 if the request fails
     */
    @GetMapping("/search")
    public ResponseEntity<List<SearchResultDto>> searchHero(@RequestParam String name) {
        try {
            // Create a latch to wait for the asynchronous response
            CountDownLatch latch = new CountDownLatch(1);
            SearchResponse[] responseHolder = new SearchResponse[1];
            Exception[] errorHolder = new Exception[1];

            SearchRequest request = SearchRequest.newBuilder()
                    .setName(name)
                    .build();

            // Create a StreamObserver to handle the gRPC response
            superheroProxyService.searchHero(request, new StreamObserver<SearchResponse>() {
                @Override
                public void onNext(SearchResponse response) {
                    responseHolder[0] = response;
                }

                @Override
                public void onError(Throwable t) {
                    errorHolder[0] = new Exception(t);
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            // Wait for the response with a timeout
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return ResponseEntity.badRequest().build();
            }

            if (errorHolder[0] != null) {
                throw errorHolder[0];
            }

            SearchResponse response = responseHolder[0];
            if (response == null) {
                return ResponseEntity.badRequest().build();
            }

            // Convert the response to DTOs
            List<SearchResultDto> results = response.getResultsList().stream()
                    .map(hero -> new SearchResultDto(hero.getId(), hero.getName()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}