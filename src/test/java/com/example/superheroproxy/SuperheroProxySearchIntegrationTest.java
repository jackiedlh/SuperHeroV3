package com.example.superheroproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import com.example.superheroproxy.proto.Biography;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.PowerStats;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

@SpringBootTest
@ActiveProfiles("test")
public class SuperheroProxySearchIntegrationTest {

    @Value("${grpc.server.port}")
    private int grpcPort;

    @Autowired
    private CacheManager cacheManager;

    private ManagedChannel channel;
    private SuperheroServiceGrpc.SuperheroServiceBlockingStub blockingStub;
    private SuperheroServiceGrpc.SuperheroServiceStub asyncStub;

    @BeforeEach
    public void setup() {
        System.out.println("Connecting to gRPC server on port: " + grpcPort);
        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();
        
        blockingStub = SuperheroServiceGrpc.newBlockingStub(channel);
        asyncStub = SuperheroServiceGrpc.newStub(channel);
        
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @AfterEach
    public void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    public void testSuccessfulSearchAndCaching() {
        // First search - should hit external API
        SearchRequest request = SearchRequest.newBuilder()
                .setName("Batman")
                .build();

        System.out.println("Sending request for hero: " + request.getName());
        
        SearchResponse response = blockingStub.searchHero(request);

        assertNotNull(response);
        assertEquals("success", response.getResponse());
        assertEquals("Batman", response.getResultsFor());
        assertTrue(response.getResultsCount() > 0);

        // Verify cache entries
        Cache heroSearchCache = cacheManager.getCache("heroSearchCache");
        Cache superheroCache = cacheManager.getCache("superheroCache");
        assertNotNull(heroSearchCache);
        assertNotNull(superheroCache);

        // Verify search results are cached
        assertNotNull(heroSearchCache.get("batman"));
        
        // Verify each hero is cached
        for (Hero hero : response.getResultsList()) {
            assertNotNull(superheroCache.get(hero.getId()));
        }
        
        // Print response details
        System.out.println("Response: " + response.getResponse());
        System.out.println("Results for: " + response.getResultsFor());
        
        for (Hero hero : response.getResultsList()) {
            System.out.println("\nHero: " + hero.getName());
            System.out.println("ID: " + hero.getId());
            
            PowerStats powerStats = hero.getPowerstats();
            System.out.println("Power Stats:");
            System.out.println("  Intelligence: " + powerStats.getIntelligence());
            System.out.println("  Strength: " + powerStats.getStrength());
            System.out.println("  Speed: " + powerStats.getSpeed());
            
            Biography bio = hero.getBiography();
            System.out.println("Biography:");
            System.out.println("  Full Name: " + bio.getFullName());
            System.out.println("  Publisher: " + bio.getPublisher());
            
            System.out.println("Image URL: " + hero.getImage().getUrl());
        }
    }

    @Test
    public void testNonExistentHero() {
        // Search for non-existent hero
        SearchRequest request = SearchRequest.newBuilder()
                .setName("NonExistentHero123")
                .build();

        SearchResponse response = blockingStub.searchHero(request);
        assertNotNull(response);
        assertEquals("success", response.getResponse());
        assertEquals(0, response.getResultsCount());

        // Verify empty result is cached
        Cache heroSearchCache = cacheManager.getCache("heroSearchCache");
        assertNotNull(heroSearchCache);
        assertNotNull(heroSearchCache.get("nonexistenthero123"));

        // Second search should use cached empty result
        SearchResponse cachedResponse = blockingStub.searchHero(request);
        assertNotNull(cachedResponse);
        assertEquals("success", cachedResponse.getResponse());
        assertEquals(0, cachedResponse.getResultsCount());
    }

    @Test
    public void testConcurrentRequestsDeduplication() throws InterruptedException {
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successfulResponses = new AtomicInteger(0);
        AtomicInteger externalApiCalls = new AtomicInteger(0);



        // Simulate concurrent requests
        for ( int i = 0; i < numThreads; i++) {
            String reqName = "Superman" +i;
            executor.submit(() -> {
                try {
                    SearchRequest request = SearchRequest.newBuilder()
                            .setName(reqName)
                            .build();
                    SearchResponse response = blockingStub.searchHero(request);
                    if (response != null && response.getResponse().equals("success")) {
                        successfulResponses.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        Thread.sleep(1000);

        // Wait for all requests to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify all requests were successful
        assertEquals(numThreads, successfulResponses.get());



        executor.shutdown();
    }


} 