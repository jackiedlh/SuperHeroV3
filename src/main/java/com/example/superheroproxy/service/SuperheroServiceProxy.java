package com.example.superheroproxy.service;

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
public class SuperheroServiceProxy extends SuperheroServiceGrpc.SuperheroServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(SuperheroServiceProxy.class);

    private final SuperheroSearchService superheroSearchService;

    @Autowired
    public SuperheroServiceProxy(SuperheroSearchService superheroSearchService) {
        this.superheroSearchService = superheroSearchService;
    }

    @Override
    public void searchHero(SearchRequest request, StreamObserver<SearchResponse> responseObserver) {
        try {
            SearchResponse response = superheroSearchService.searchHero(request.getName());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error("Error processing request", e);
            responseObserver.onError(e);
        }
    }
}