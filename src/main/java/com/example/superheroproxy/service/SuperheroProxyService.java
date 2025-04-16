package com.example.superheroproxy.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
 * - Request deduplication
 * - Cache penetration protection
 * 
 * This is the main entry point for gRPC clients to interact with the superhero data system.
 */
@GrpcService
public class SuperheroProxyService extends SuperheroServiceGrpc.SuperheroServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(SuperheroProxyService.class);

    private final SuperheroInnerService superheroInnerService;
    private final RateLimiter rateLimiter;
    
    // Cache for storing empty results to prevent cache penetration
    private final Cache<String, Boolean> emptyResultCache;
    // Map for request deduplication and synchronization
    private final ConcurrentHashMap<String, ReentrantLock> requestLocks;

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
        
        // Initialize empty result cache with 5 minutes expiration
        this.emptyResultCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
                
        this.requestLocks = new ConcurrentHashMap<>();
    }

    /**
     * Handles search requests for heroes by name.
     * This method:
     * 1. Checks if we can acquire a permit within a reasonable time
     * 2. Implements request deduplication
     * 3. Implements cache penetration protection
     * 4. Retrieves matching hero IDs
     * 5. Fetches detailed hero information for each ID
     * 6. Streams the results back to the client
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

            String normalizedName = request.getName().toLowerCase();
            
            // Check empty result cache first
            if (emptyResultCache.getIfPresent(normalizedName) != null) {
                logger.debug("Empty result found in cache for name: {}", request.getName());
                responseObserver.onNext(SearchResponse.newBuilder()
                    .setResponse("success")
                    .setResultsFor(request.getName())
                    .build());
                responseObserver.onCompleted();
                return;
            }

            // Get or create a lock for request deduplication
            ReentrantLock lock = requestLocks.computeIfAbsent(normalizedName, k -> new ReentrantLock());
            lock.lock();
            try {
                // Double-check empty result cache to prevent race conditions
                if (emptyResultCache.getIfPresent(normalizedName) != null) {
                    responseObserver.onNext(SearchResponse.newBuilder()
                        .setResponse("success")
                        .setResultsFor(request.getName())
                        .build());
                    responseObserver.onCompleted();
                    return;
                }

                SearchResponse.Builder responseBuilder = SearchResponse.newBuilder()
                        .setResponse("success")
                        .setResultsFor(request.getName());
                
                Set<String> ids = superheroInnerService.searchHeroIds(request.getName());
                if (ids == null) {
                    // Cache empty result to prevent future cache penetration
                    emptyResultCache.put(normalizedName, true);
                    responseObserver.onNext(responseBuilder.build());
                    responseObserver.onCompleted();
                    return;
                }

                ids.forEach(id -> {
                    Hero hero = superheroInnerService.getHero(id);
                    if (hero != null) {
                        responseBuilder.addResults(hero);
                    }
                });

                // Cache empty result if no heroes found
                if (responseBuilder.getResultsCount() == 0) {
                    emptyResultCache.put(normalizedName, true);
                }

                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
            } finally {
                lock.unlock();
                // Only remove the lock if no one is waiting for it
                if (!lock.hasQueuedThreads()) {
                    requestLocks.remove(normalizedName);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing request", e);
            responseObserver.onError(e);
        }
    }
}