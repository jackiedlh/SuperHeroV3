package com.example.superheroproxy.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;
import com.google.common.util.concurrent.RateLimiter;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * A gRPC service that acts as a proxy for superhero data operations.
 * This service provides a gRPC interface for searching and retrieving superhero information,
 * delegating the actual data operations to the SuperheroInnerService.
 * 
 * The service implements the SuperheroServiceGrpc interface and handles:
 * - Search operations for heroes by name
 * - Error handling and logging
 * - Response streaming to clients
 * 
 * This is the main entry point for gRPC clients to interact with the superhero data system.
 */
@GrpcService
public class SuperheroProxyService extends SuperheroServiceGrpc.SuperheroServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(SuperheroProxyService.class);

    private final SuperheroInnerService superheroInnerService;
    private final RateLimiter rateLimiter;

    /**
     * Constructs a new SuperheroProxyService with the specified inner service and rate limiter.
     * 
     * @param superheroInnerService The service that handles the actual superhero data operations
     * @param rateLimiter The rate limiter for controlling the rate of requests
     */
    @Autowired
    public SuperheroProxyService(SuperheroInnerService superheroInnerService, RateLimiter rateLimiter) {
        this.superheroInnerService = superheroInnerService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Handles search requests for heroes by name.
     * This method:
     * 1. Checks if we can acquire a permit within a reasonable time
     * 2. Retrieves matching hero IDs
     * 3. Fetches detailed hero information for each ID
     * 4. Streams the results back to the client
     * 
     * @param request The search request containing the hero name to search for
     * @param responseObserver The stream observer for sending responses back to the client
     */
    @Override
    public void searchHero(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        try {
            // Check if we can acquire a permit within a reasonable time
            if (!rateLimiter.tryAcquire()) {
                logger.warn("Rate limit exceeded for request: {}", request.getName());
                responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("Rate limit exceeded. Please try again later.")
                    .asRuntimeException());
                return;
            }

            SearchResponse.Builder responseBuilder = SearchResponse.newBuilder()
                    .setResponse("success")
                    .setResultsFor(request.getName());
            Set<String> ids = superheroInnerService.searchHeroIds(request.getName());
            ids.forEach(id -> {
                Hero hero = superheroInnerService.getHero(id);
                responseBuilder.addResults(hero);
            });

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error processing request", e);
            responseObserver.onError(e);
        }
    }
}