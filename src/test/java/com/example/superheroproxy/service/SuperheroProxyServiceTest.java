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

public class SuperheroProxyServiceTest {

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
    private SuperheroProxyService superheroService;
    private SuperheroInnerService superheroInnerService;
    private CacheManager cacheManager;
    private StreamObserver<SearchResponse> responseObserver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = mock(TestRestTemplate.class);
        superheroInnerService = mock(SuperheroInnerService.class);
        cacheManager = new CaffeineCacheManager();
        responseObserver = new TestStreamObserver();
        objectMapper = new ObjectMapper();
        superheroService = new SuperheroProxyService(superheroInnerService);
    }

    @Test
    void testSearchHero_Success() throws Exception {
        SearchRequest request = SearchRequest.newBuilder().setName("batman").build();
        SearchResponse expectedResponse = SearchResponse.newBuilder().build();
        
        when(superheroInnerService.searchHero(any())).thenReturn(expectedResponse);
        
        superheroService.searchHero(request, responseObserver);
        
        SearchResponse actualResponse = ((TestStreamObserver) responseObserver).getResponse();
        verify(superheroInnerService).searchHero("batman");
    }

    @Test
    void testSearchHero_Error() throws Exception {
        SearchRequest request = SearchRequest.newBuilder().setName("batman").build();
        
        when(superheroInnerService.searchHero(any())).thenThrow(new RuntimeException("Test error"));
        
        superheroService.searchHero(request, responseObserver);
        
        Throwable error = ((TestStreamObserver) responseObserver).getError();
        verify(superheroInnerService).searchHero("batman");
    }
} 