package com.example.superheroproxy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.example.superheroproxy.proto.SearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.service.SuperheroSearchService;

import io.grpc.stub.StreamObserver;

@SpringBootTest
@ActiveProfiles("test")
public class NotificationClientTest {

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private SuperheroSearchService superheroSearchService;

    @Autowired
    private CacheManager cacheManager;

    private CountDownLatch latch;
    private List<HeroUpdate> receivedUpdates;

    @BeforeEach
    void setUp() {
        receivedUpdates = new ArrayList<>();
        // Clear the cache before each test
        cacheManager.getCache("superheroCache").clear();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        notificationClient.shutdown();
    }

    @Test
    void testSubscribeToUpdates() throws InterruptedException {
        // Create a list of heroes to monitor
        List<String> heroNames = List.of("spider-man");

        // First, search for the hero to get the expected number of results
        SearchResponse resp = superheroSearchService.searchHero("spider-man");
        int expectedUpdates = resp.getResultsList().size();
        
        // Create a latch that will count down for each expected update
        latch = new CountDownLatch(expectedUpdates);

        // Create a StreamObserver to handle updates
        StreamObserver<HeroUpdate> responseObserver = new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                receivedUpdates.add(update);
                System.out.println("Received update for hero: " + update.getHero().getName());

                // Print hero details
                var hero = update.getHero();
                System.out.println("Hero details:");
                System.out.println("  ID: " + hero.getId());
                System.out.println("  Name: " + hero.getName());
                System.out.println("  Intelligence: " + hero.getPowerstats().getIntelligence());
                System.out.println("  Full Name: " + hero.getBiography().getFullName());
                System.out.println("  Publisher: " + hero.getBiography().getPublisher());
                
                // Count down the latch for each update
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error in notification stream: " + t.getMessage());
                // Count down all remaining latches on error
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
            }

            @Override
            public void onCompleted() {
                System.out.println("Notification stream completed");
                // Count down all remaining latches on completion
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
            }
        };

        // Subscribe to updates
        notificationClient.subscribeToUpdates(heroNames, responseObserver);

        // Clear the cache to ensure we get a cache miss
        cacheManager.getCache("superheroCache").clear();

        // Trigger a search after subscribing to ensure we get updates
        superheroSearchService.searchHero("spider-man");

        // Wait for all expected updates with a timeout
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Did not receive all updates within timeout");
        assertEquals(expectedUpdates, receivedUpdates.size(), "Should have received exactly " + expectedUpdates + " updates");
        
        // Verify each update
        for (HeroUpdate update : receivedUpdates) {
            assertEquals("spider-man", update.getHero().getName().toLowerCase(), "Update should be for spider-man");
        }
    }
} 