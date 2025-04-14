package com.example.superheroproxy.service;

import com.example.superheroproxy.config.TestRestTemplate;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = SuperheroProxyServiceTest.TestConfig.class)
class SuperheroProxyServiceTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        private String mockResponse = "{\"response\":\"success\",\"results-for\":\"spider-man\",\"results\":[{\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"90\",\"strength\":\"55\",\"speed\":\"67\"},\"biography\":{\"full-name\":\"Peter Parker\",\"publisher\":\"Marvel Comics\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}]}";

        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager cacheManager = new SimpleCacheManager();
            cacheManager.setCaches(Arrays.asList(
                    new ConcurrentMapCache("superheroCache"), 
                    new ConcurrentMapCache("heroSearchCache")
            ));
            return cacheManager;
        }

        @Bean
        TestRestTemplate restTemplate() {
            TestRestTemplate template = new TestRestTemplate();
            template.setResponse(mockResponse);
            return template;
        }

        @Bean
        CacheUpdateScheduleService cacheUpdateService() {
            return mock(CacheUpdateScheduleService.class);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        ExternalApiService externalAPIService() {
            ExternalApiService mockService = mock(ExternalApiService.class);
            try {
                when(mockService.searchHero(anyString())).thenReturn(SearchResponse.newBuilder()
                    .addResults(Hero.newBuilder()
                        .setId("1")
                        .setName("Test Hero")
                        .build())
                    .build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return mockService;
        }

        @Bean
        SuperheroInnerService superheroInnerService(
                TestRestTemplate restTemplate, 
                CacheUpdateScheduleService cacheUpdateScheduleService,
                NotificationService notificationService,
                CacheManager cacheManager,
                ExternalApiService externalAPIService) {
            SuperheroInnerService service = new SuperheroInnerService(restTemplate, cacheUpdateScheduleService, notificationService, externalAPIService);
            return service;
        }

        @Bean
        SuperheroProxyService superheroProxyService(SuperheroInnerService superheroInnerService) {
            return new SuperheroProxyService(superheroInnerService);
        }
    }

    @Autowired
    private SuperheroProxyService superheroProxyService;

    @Autowired
    private SuperheroInnerService superheroInnerService;

    @Autowired
    private CacheManager cacheManager;

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

    @BeforeEach
    void setUp() {
        // Clear caches before each test
        cacheManager.getCache("superheroCache").clear();
        cacheManager.getCache("heroSearchCache").clear();
    }

    @Test
    void testSearchHero_Success() throws Exception {
        // Setup
        SearchRequest request = SearchRequest.newBuilder()
                .setName("spider-man")
                .build();
        TestStreamObserver responseObserver = new TestStreamObserver();

        // Mock the inner service responses
        when(superheroInnerService.searchHeroIds("spider-man")).thenReturn(Set.of("620"));
        Hero mockHero = Hero.newBuilder()
                .setId("620")
                .setName("Spider-Man")
                .build();
        when(superheroInnerService.getHero("620")).thenReturn(mockHero);

        // Execute
        superheroProxyService.searchHero(request, responseObserver);

        // Verify
        SearchResponse response = responseObserver.getResponse();
        assertNotNull(response);
        assertEquals("success", response.getResponse());
        assertEquals("spider-man", response.getResultsFor());
        assertEquals(1, response.getResultsCount());
        assertEquals("620", response.getResults(0).getId());
        assertEquals("Spider-Man", response.getResults(0).getName());
    }

    @Test
    void testSearchHero_Error() throws Exception {
        // Setup
        SearchRequest request = SearchRequest.newBuilder()
                .setName("spider-man")
                .build();
        TestStreamObserver responseObserver = new TestStreamObserver();

        // Mock an error in the inner service
        when(superheroInnerService.searchHeroIds("spider-man")).thenThrow(new RuntimeException("API Error"));

        // Execute
        superheroProxyService.searchHero(request, responseObserver);

        // Verify
        Throwable error = responseObserver.getError();
        assertNotNull(error);
        assertTrue(error.getMessage().contains("API Error"));
    }

    @Test
    void testSearchHero_EmptyResults() throws Exception {
        // Setup
        SearchRequest request = SearchRequest.newBuilder()
                .setName("nonexistent")
                .build();
        TestStreamObserver responseObserver = new TestStreamObserver();

        // Mock empty results
        when(superheroInnerService.searchHeroIds("nonexistent")).thenReturn(Set.of());

        // Execute
        superheroProxyService.searchHero(request, responseObserver);

        // Verify
        SearchResponse response = responseObserver.getResponse();
        assertNotNull(response);
        assertEquals("success", response.getResponse());
        assertEquals("nonexistent", response.getResultsFor());
        assertEquals(0, response.getResultsCount());
    }
} 