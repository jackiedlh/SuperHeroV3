package com.example.superheroproxy.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;

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

    /**
     * Constructs a new SuperheroProxyService with the specified inner service.
     * 
     * @param superheroInnerService The service that handles the actual superhero data operations
     */
    @Autowired
    public SuperheroProxyService(SuperheroInnerService superheroInnerService) {
        this.superheroInnerService = superheroInnerService;
    }

    /**
     * Handles search requests for heroes by name.
     * This method:
     * 1. Processes the search request
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