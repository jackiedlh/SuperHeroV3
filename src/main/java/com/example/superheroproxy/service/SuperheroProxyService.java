package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * 
 * This class implements the SuperheroServiceGrpc interface and provides the implementation for the gRPC service.
 */
@GrpcService
public class SuperheroProxyService extends SuperheroServiceGrpc.SuperheroServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(SuperheroProxyService.class);

    private final SuperheroInnerService superheroInnerService;

    @Autowired
    public SuperheroProxyService(SuperheroInnerService superheroInnerService) {
        this.superheroInnerService = superheroInnerService;
    }

    @Override
    public void searchHero(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        try {
            SearchResponse.Builder responseBuilder = SearchResponse.newBuilder()
                    .setResponse("success")
                    .setResultsFor(request.getName());
            SearchResponse nameResp = superheroInnerService.searchHero(request.getName());
            nameResp.getResultsList().forEach(h -> {
                Hero hero = superheroInnerService.getHero(h.getId());
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