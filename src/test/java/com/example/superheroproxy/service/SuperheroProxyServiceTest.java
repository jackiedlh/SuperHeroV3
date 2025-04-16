package com.example.superheroproxy.service;

import com.example.superheroproxy.config.TestRestTemplate;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.google.common.util.concurrent.RateLimiter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperheroProxyServiceTest {

    @Mock
    private SuperheroInnerService superheroInnerService;

    private SuperheroProxyService superheroProxyService;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = RateLimiter.create(10.0); // 10 requests per second
        superheroProxyService = new SuperheroProxyService(superheroInnerService, rateLimiter);
    }

    private static class TestStreamObserver implements io.grpc.stub.StreamObserver<SearchResponse> {
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

    @Test
    void testSearchHero_Success() throws Exception {
        // Setup mocks for successful response
        Set<String> heroIds = new HashSet<>();
        heroIds.add("1");
        when(superheroInnerService.searchHeroIds("spider-man")).thenReturn(heroIds);
        when(superheroInnerService.getHero("1")).thenReturn(
            Hero.newBuilder()
                .setId("1")
                .setName("Test Hero")
                .build()
        );

        TestStreamObserver responseObserver = new TestStreamObserver();
        superheroProxyService.searchHero(SearchRequest.newBuilder().setName("spider-man").build(), responseObserver);
        
        SearchResponse response = responseObserver.getResponse();
        assertNotNull(response);
        assertFalse(response.getResultsList().isEmpty());
        assertEquals("Test Hero", response.getResults(0).getName());
    }

    @Test
    void testSearchHero_Error() throws Exception {
        // Mock error response for empty name
        when(superheroInnerService.searchHeroIds("")).thenThrow(
            new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Name cannot be empty"))
        );

        TestStreamObserver responseObserver = new TestStreamObserver();
        superheroProxyService.searchHero(SearchRequest.newBuilder().setName("").build(), responseObserver);
        
        Throwable error = responseObserver.getError();
        assertNotNull(error);
        assertTrue(error instanceof StatusRuntimeException);
        assertEquals(Status.INVALID_ARGUMENT.getCode(), ((StatusRuntimeException) error).getStatus().getCode());
    }

    @Test
    void testSearchHero_EmptyResults() throws Exception {
        // Mock empty response for nonexistent hero
        when(superheroInnerService.searchHeroIds("nonexistent")).thenReturn(Collections.emptySet());

        TestStreamObserver responseObserver = new TestStreamObserver();
        superheroProxyService.searchHero(SearchRequest.newBuilder().setName("nonexistent").build(), responseObserver);
        
        SearchResponse response = responseObserver.getResponse();
        assertNotNull(response);
        assertTrue(response.getResultsList().isEmpty());
    }

    @Test
    void testSearchHero_RateLimitExceeded() throws Exception {
        // Create service with a very low rate limit
        RateLimiter strictRateLimiter = RateLimiter.create(0.1); // 1 request per 10 seconds
        SuperheroProxyService rateLimitedService = new SuperheroProxyService(superheroInnerService, strictRateLimiter);

        // Setup mocks for successful response
        Set<String> heroIds = new HashSet<>();
        heroIds.add("1");
        when(superheroInnerService.searchHeroIds("spider-man")).thenReturn(heroIds);
        when(superheroInnerService.getHero("1")).thenReturn(
            Hero.newBuilder()
                .setId("1")
                .setName("Test Hero")
                .build()
        );

        // First request should succeed
        TestStreamObserver firstObserver = new TestStreamObserver();
        rateLimitedService.searchHero(SearchRequest.newBuilder().setName("spider-man").build(), firstObserver);
        assertNotNull(firstObserver.getResponse());

        // Second request should fail due to rate limiting
        TestStreamObserver secondObserver = new TestStreamObserver();
        rateLimitedService.searchHero(SearchRequest.newBuilder().setName("spider-man").build(), secondObserver);
        
        Throwable error = secondObserver.getError();
        assertNotNull(error);
        assertTrue(error instanceof StatusRuntimeException);
        assertEquals(Status.RESOURCE_EXHAUSTED.getCode(), ((StatusRuntimeException) error).getStatus().getCode());
    }
} 