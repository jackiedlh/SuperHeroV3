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
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SuperheroProxyServiceTest {

    @Mock
    private SuperheroInnerService superheroInnerService;

    @Mock
    private io.grpc.stub.StreamObserver<SearchResponse> responseObserver;

    private CacheManager cacheManager;
    private RateLimiter rateLimiter;
    private SuperheroProxyService superheroProxyService;

    @BeforeEach
    public void setup() {
        // Initialize cache manager with required caches
        cacheManager = new ConcurrentMapCacheManager("heroSearchCache", "superheroCache", "emptyResultCache");
        
        // Create a rate limiter that allows 10 requests per second
        rateLimiter = RateLimiter.create(10.0);
        
        // Initialize the service
        superheroProxyService = new SuperheroProxyService(superheroInnerService, rateLimiter);
        
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
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
    public void testSuccessfulSearch() {
        // Setup test data
        String heroName = "Batman";
        String heroId = "123";
        Hero mockHero = Hero.newBuilder()
                .setId(heroId)
                .setName(heroName)
                .build();

        // Mock inner service behavior
        doReturn(Set.of(heroId)).when(superheroInnerService).searchHeroIds(heroName);
        doReturn(mockHero).when(superheroInnerService).getHero(heroId);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
                .setName(heroName)
                .build();

        // Execute test
        superheroProxyService.searchHero(request, responseObserver);

        // Verify response
        verify(responseObserver).onNext(any(SearchResponse.class));
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any(Throwable.class));
    }

    @Test
    public void testConcurrentRequestsDeduplication() throws InterruptedException {
        // Setup test data
        String heroName = "Superman";
        String heroId = "456";
        Hero mockHero = Hero.newBuilder()
                .setId(heroId)
                .setName(heroName)
                .build();

        // Mock inner service behavior
        doReturn(Set.of(heroId)).when(superheroInnerService).searchHeroIds(heroName);
        doReturn(mockHero).when(superheroInnerService).getHero(heroId);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
                .setName(heroName)
                .build();

        // Setup concurrent test
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successfulResponses = new AtomicInteger(0);

        // Simulate concurrent requests
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    io.grpc.stub.StreamObserver<SearchResponse> observer = mock(io.grpc.stub.StreamObserver.class);
                    superheroProxyService.searchHero(request, observer);
                    successfulResponses.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all requests to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify all requests were successful
        assertEquals(numThreads, successfulResponses.get());

        // Verify inner service was called only once for search and once for getHero
        verify(superheroInnerService, times(1)).searchHeroIds(heroName);
        verify(superheroInnerService, times(1)).getHero(heroId);

        executor.shutdown();
    }

    @Test
    public void testRateLimiting() {
        // Create a rate limiter that allows only 1 request per second
        RateLimiter strictRateLimiter = RateLimiter.create(1.0);
        SuperheroProxyService strictService = new SuperheroProxyService(superheroInnerService, strictRateLimiter);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
                .setName("Batman")
                .build();

        // First request should succeed
        strictService.searchHero(request, responseObserver);
        verify(responseObserver).onNext(any(SearchResponse.class));
        verify(responseObserver).onCompleted();

        // Reset mock
        verify(responseObserver, times(1)).onNext(any(SearchResponse.class));

        // Second request should be rate limited
        strictService.searchHero(request, responseObserver);
        verify(responseObserver).onError(any(StatusRuntimeException.class));
    }

    @Test
    public void testErrorHandling() {
        // Setup test data
        String heroName = "ErrorHero";
        RuntimeException testException = new RuntimeException("Test error");

        // Mock inner service to throw exception
        doThrow(testException).when(superheroInnerService).searchHeroIds(heroName);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
                .setName(heroName)
                .build();

        // Execute test
        superheroProxyService.searchHero(request, responseObserver);

        // Verify error handling
        verify(responseObserver).onError(any(Throwable.class));
        verify(responseObserver, never()).onNext(any(SearchResponse.class));
        verify(responseObserver, never()).onCompleted();
    }
} 