package com.example.superheroproxy.service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.example.superheroproxy.config.AppConfig;
import com.example.superheroproxy.config.AsyncConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.google.common.util.concurrent.RateLimiter;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

@ExtendWith(MockitoExtension.class)
public class SuperheroProxyServiceTest {

    @Mock
    private SuperheroInnerService superheroInnerService;

    @Mock
    private io.grpc.stub.StreamObserver<SearchResponse> responseObserver;

    private CacheManager cacheManager;

    private AppConfig appConfig;

    @BeforeEach
    public void setup() {
        // Initialize cache manager with required caches
        cacheManager = new ConcurrentMapCacheManager("heroSearchCache", "superheroCache", "emptyResultCache");
        
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });

        // Create AppConfig with AsyncConfig
        AsyncConfig asyncConfig = new AsyncConfig();
        asyncConfig.setCorePoolSize(2);
        asyncConfig.setMaxPoolSize(4);
        asyncConfig.setQueueCapacity(50);
        asyncConfig.setThreadNamePrefix("Test-Async-");
        appConfig = new AppConfig(asyncConfig);

        // Reset mock between tests
        reset(superheroInnerService);
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
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new InterruptedException("Timeout waiting for response");
            }
            if (error != null) {
                throw new RuntimeException("Error in stream", error);
            }
            return response;
        }

        public Throwable getError() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
            return error;
        }
    }

    @Test
    public void testSuccessfulSearch() {
        // Create a rate limiter that allows 10 requests per second
        RateLimiter rateLimiter = RateLimiter.create(10.0);
        SuperheroProxyService superheroProxyService = new SuperheroProxyService(superheroInnerService, rateLimiter,appConfig);

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
        TestStreamObserver observer = new TestStreamObserver();
        superheroProxyService.searchHero(request, observer);

        try {
            SearchResponse response = observer.getResponse();
            assertNotNull(response);
            assertEquals("success", response.getResponse());
            assertEquals(1, response.getResultsCount());
            assertEquals(heroName, response.getResults(0).getName());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }



    @Test
    public void testErrorHandling() {
        // Create a rate limiter that allows 10 requests per second
        RateLimiter rateLimiter = RateLimiter.create(10.0);
        SuperheroProxyService superheroProxyService = new SuperheroProxyService(superheroInnerService, rateLimiter,appConfig);

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
        TestStreamObserver observer = new TestStreamObserver();
        superheroProxyService.searchHero(request, observer);

        try {
            observer.getResponse();
            fail("Expected error");
        } catch (RuntimeException e) {
            assertEquals("Test error", e.getCause().getMessage());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }

    @Test
    public void testPagination() throws InterruptedException {
        // Create a rate limiter that allows 100 requests per second for pagination test
        RateLimiter rateLimiter = RateLimiter.create(100.0);
        SuperheroProxyService superheroProxyService = new SuperheroProxyService(superheroInnerService, rateLimiter,appConfig);

        // Setup test data - create 5 heroes
        Set<String> heroIds = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            String heroId = String.valueOf(i);
            heroIds.add(heroId);
            
            Hero mockHero = Hero.newBuilder()
                    .setId(heroId)
                    .setName("Hero" + i)
                    .build();
            
            doReturn(mockHero).when(superheroInnerService).getHero(heroId);
        }

        // Mock inner service to return all hero IDs
        doReturn(heroIds).when(superheroInnerService).searchHeroIds("hero");

        // Test case 1: Page size 2, first page
        SearchRequest request1 = SearchRequest.newBuilder()
                .setName("hero")
                .setPageSize(2)
                .setPageNumber(1)
                .build();

        TestStreamObserver observer1 = new TestStreamObserver();
        superheroProxyService.searchHero(request1, observer1);
        
        try {
            SearchResponse response1 = observer1.getResponse();
            assertNotNull(response1);
            assertEquals(5, response1.getTotalCount());
            assertEquals(1, response1.getCurrentPage());
            assertEquals(3, response1.getTotalPages()); // ceil(5/2) = 3
            assertEquals(2, response1.getResultsCount());
            assertEquals("Hero1", response1.getResults(0).getName());
            assertEquals("Hero2", response1.getResults(1).getName());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        Thread.sleep(100);

        // Test case 2: Page size 2, second page
        SearchRequest request2 = SearchRequest.newBuilder()
                .setName("hero")
                .setPageSize(2)
                .setPageNumber(2)
                .build();

        TestStreamObserver observer2 = new TestStreamObserver();
        superheroProxyService.searchHero(request2, observer2);
        
        try {
            SearchResponse response2 = observer2.getResponse();
            assertNotNull(response2);
            assertEquals(5, response2.getTotalCount());
            assertEquals(2, response2.getCurrentPage());
            assertEquals(3, response2.getTotalPages());
            assertEquals(2, response2.getResultsCount());
            assertEquals("Hero3", response2.getResults(0).getName());
            assertEquals("Hero4", response2.getResults(1).getName());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        Thread.sleep(100);

        // Test case 3: Page size 2, last page (should have only 1 result)
        SearchRequest request3 = SearchRequest.newBuilder()
                .setName("hero")
                .setPageSize(2)
                .setPageNumber(3)
                .build();

        TestStreamObserver observer3 = new TestStreamObserver();
        superheroProxyService.searchHero(request3, observer3);
        
        try {
            SearchResponse response3 = observer3.getResponse();
            assertNotNull(response3);
            assertEquals(5, response3.getTotalCount());
            assertEquals(3, response3.getCurrentPage());
            assertEquals(3, response3.getTotalPages());
            assertEquals(1, response3.getResultsCount());
            assertEquals("Hero5", response3.getResults(0).getName());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        Thread.sleep(100);

        // Test case 4: Page number exceeds total pages (should return last page)
        SearchRequest request4 = SearchRequest.newBuilder()
                .setName("hero")
                .setPageSize(2)
                .setPageNumber(4)
                .build();

        TestStreamObserver observer4 = new TestStreamObserver();
        superheroProxyService.searchHero(request4, observer4);
        
        try {
            SearchResponse response4 = observer4.getResponse();
            assertNotNull(response4);
            assertEquals(5, response4.getTotalCount());
            assertEquals(3, response4.getCurrentPage()); // Should be adjusted to last page
            assertEquals(3, response4.getTotalPages());
            assertEquals(1, response4.getResultsCount());
            assertEquals("Hero5", response4.getResults(0).getName());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }


    @Test
    public void testRateLimiting() throws InterruptedException {
        // Create a rate limiter that allows only 1 request per second with no burst
        RateLimiter strictRateLimiter = RateLimiter.create(1.0);
        SuperheroProxyService strictService = new SuperheroProxyService(superheroInnerService, strictRateLimiter,appConfig);

        // Setup mock to return a hero ID
        String heroId = "123";
        Hero mockHero = Hero.newBuilder()
                .setId(heroId)
                .setName("Batman")
                .build();

        doReturn(Set.of(heroId)).when(superheroInnerService).searchHeroIds("Batman");
        doReturn(mockHero).when(superheroInnerService).getHero(heroId);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
                .setName("Batman")
                .setPageSize(10)
                .setPageNumber(1)
                .build();

        // First request should succeed
        TestStreamObserver observer1 = new TestStreamObserver();
        strictService.searchHero(request, observer1);

        try {
            SearchResponse response = observer1.getResponse();
            assertNotNull(response);
            assertEquals("success", response.getResponse());
            assertEquals(1, response.getResultsCount());
            assertEquals("Batman", response.getResults(0).getName());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Make second request immediately after first
        TestStreamObserver observer2 = new TestStreamObserver();
        strictService.searchHero(request, observer2);

        try {
            // Wait for the response with a short timeout
            observer2.getResponse();
            fail("Expected rate limit exception");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof StatusRuntimeException);
            assertEquals(Status.Code.RESOURCE_EXHAUSTED, ((StatusRuntimeException) e.getCause()).getStatus().getCode());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
    }
} 