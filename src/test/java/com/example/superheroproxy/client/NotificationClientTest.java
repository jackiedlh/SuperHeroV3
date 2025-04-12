package com.example.superheroproxy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    private CountDownLatch latch;
    private List<HeroUpdate> receivedUpdates;

    @BeforeEach
    void setUp() {
        latch = new CountDownLatch(1);
        receivedUpdates = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        notificationClient.shutdown();
    }

    @Test
    void testSubscribeToUpdates() throws InterruptedException {
        // Create a list of heroes to monitor
        List<String> heroNames = List.of("spider-man");

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
                
                // Count down the latch when we receive the update
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error in notification stream: " + t.getMessage());
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Notification stream completed");
                latch.countDown();
            }
        };

        // Subscribe to updates
        notificationClient.subscribeToUpdates(heroNames, responseObserver);

        // Trigger a cache update by searching for the hero
        superheroSearchService.searchHero("spider-man");

        // Wait for the update with a timeout
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Did not receive update within timeout");
//        assertEquals(1, receivedUpdates.size(), "Should have received exactly one update");
        
        HeroUpdate update = receivedUpdates.get(0);
        assertEquals("spider-man", update.getHero().getName().toLowerCase(), "Update should be for spider-man");
    }
} 