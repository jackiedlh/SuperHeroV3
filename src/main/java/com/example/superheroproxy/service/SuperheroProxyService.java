package com.example.superheroproxy.service;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.example.superheroproxy.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
    private static final int MAX_THREADS = 10; // Maximum number of concurrent getHero calls

    private final SuperheroInnerService superheroInnerService;
    private final RateLimiter rateLimiter;
    private final Executor executor;
    private final AppConfig appConfig;

    /**
     * Constructs a new SuperheroProxyService with the specified inner service and rate limiter.
     * 
     * @param superheroInnerService The service that handles the actual superhero data operations
     * @param rateLimiter The rate limiter for controlling the rate of requests
     */
    @Autowired
    public SuperheroProxyService(SuperheroInnerService superheroInnerService, RateLimiter rateLimiter, AppConfig appConfig) {
        this.superheroInnerService = superheroInnerService;
        this.rateLimiter = rateLimiter;
        this.appConfig = appConfig;
//        this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
        this.executor = appConfig.getAsyncExecutor();
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

            // Get all matching IDs first
            Set<String> allIds = superheroInnerService.searchHeroIds(request.getName()); //count all + current page ID
            int totalCount = allIds.size();
            
            // Calculate pagination
            int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10; // Default page size
            int pageNumber = request.getPageNumber() > 0 ? request.getPageNumber() : 1; // Default to first page
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            
            // Validate page number
            if (pageNumber > totalPages) {
                pageNumber = totalPages;
            }
            
            // Calculate start and end indices for the current page
            int startIndex = pageNumber==0 ? 0 :(pageNumber - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalCount);
            
            // Get the subset of IDs for the current page
            var pageIds = allIds.stream()
                .skip(startIndex)
                .limit(endIndex - startIndex)
                .collect(Collectors.toList());

            SearchResponse.Builder responseBuilder = SearchResponse.newBuilder()
                    .setResponse("success")
                    .setResultsFor(request.getName())
                    .setTotalCount(totalCount)
                    .setCurrentPage(pageNumber)
                    .setTotalPages(totalPages);

            // Create a list of CompletableFuture for each hero retrieval
            var heroFutures = pageIds.stream()
                .map(id -> CompletableFuture.supplyAsync(
                    () -> superheroInnerService.getHero(id),
                    executor
                ))
                .collect(Collectors.toList());

            // Wait for all futures to complete and collect results
            CompletableFuture.allOf(heroFutures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    heroFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(hero -> hero != null) // Filter out null results
                        .forEach(responseBuilder::addResults);
                    
                    responseObserver.onNext(responseBuilder.build());
                    responseObserver.onCompleted();
                })
                .exceptionally(throwable -> {
                    logger.error("Error processing hero retrieval", throwable);
                    responseObserver.onError(throwable);
                    return null;
                });

        } catch (Exception e) {
            logger.error("Error processing request", e);
            responseObserver.onError(e);
        }
    }
}