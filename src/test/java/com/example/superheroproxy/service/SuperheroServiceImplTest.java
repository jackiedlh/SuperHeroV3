package com.example.superheroproxy.service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import com.example.superheroproxy.config.TestRestTemplate;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.stub.StreamObserver;

public class SuperheroServiceImplTest {

    private static class TestStreamObserver implements StreamObserver<SearchResponse> {
        private SearchResponse response;
        private Throwable error;
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onNext(SearchResponse value) {
            this.response = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }

        public SearchResponse getResponse() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
            return response;
        }

        public Throwable getError() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
            return error;
        }
    }

    private TestRestTemplate restTemplate;
    private SuperheroServiceImpl superheroService;
    private SuperheroSearchService superheroSearchService;
    private CacheManager cacheManager;
    private StreamObserver<SearchResponse> responseObserver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        cacheManager = new CaffeineCacheManager("superheroCache");
        superheroSearchService = mock(SuperheroSearchService.class);
        superheroService = new SuperheroServiceImpl(superheroSearchService);
        responseObserver = mock(StreamObserver.class);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSearchHero_Success() throws Exception {
        // Arrange
        String heroName = "spider-man";
        SearchRequest request = SearchRequest.newBuilder()
                .setName(heroName)
                .build();

        SearchResponse mockResponse = SearchResponse.newBuilder()
                .setResponse("success")
                .setResultsFor(heroName)
                .build();

        when(superheroSearchService.searchHero(any())).thenReturn(mockResponse);

        // Act
        superheroService.searchHero(request, responseObserver);

        // Assert
        verify(responseObserver).onNext(mockResponse);
        verify(responseObserver).onCompleted();
    }

    @Test
    void testSearchHero_NoResults() throws Exception {
        // Arrange
        String heroName = "nonexistent";
        SearchRequest request = SearchRequest.newBuilder()
                .setName(heroName)
                .build();

        SearchResponse mockResponse = SearchResponse.newBuilder()
                .setResponse("success")
                .setResultsFor(heroName)
                .build();

        when(superheroSearchService.searchHero(any())).thenReturn(mockResponse);

        // Act
        superheroService.searchHero(request, responseObserver);

        // Assert
        verify(responseObserver).onNext(mockResponse);
        verify(responseObserver).onCompleted();
    }

    @Test
    void testSearchHero_Error() throws Exception {
        // Arrange
        String heroName = "error";
        SearchRequest request = SearchRequest.newBuilder()
                .setName(heroName)
                .build();

        RuntimeException exception = new RuntimeException("API Error");
        when(superheroSearchService.searchHero(any())).thenThrow(exception);

        // Act
        superheroService.searchHero(request, responseObserver);

        // Assert
        verify(responseObserver).onError(exception);
    }

    @Test
    void testSearchHero_InvalidJson() throws Exception {
        // Arrange
        String heroName = "invalid";
        SearchRequest request = SearchRequest.newBuilder()
                .setName(heroName)
                .build();

        RuntimeException exception = new RuntimeException("Invalid JSON");
        when(superheroSearchService.searchHero(any())).thenThrow(exception);

        // Act
        superheroService.searchHero(request, responseObserver);

        // Assert
        verify(responseObserver).onError(exception);
    }
} 