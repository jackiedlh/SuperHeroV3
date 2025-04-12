package com.example.superheroproxy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.SearchResponse;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SuperheroProxyClientTest {

    @Autowired
    private SuperheroProxyClient client;

    private CountDownLatch updateLatch;
    private HeroUpdate receivedUpdate;

    @BeforeEach
    void setUp() {
        updateLatch = new CountDownLatch(1);
        receivedUpdate = null;
    }

    @AfterAll
    void tearDown() throws InterruptedException {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void testSearchHero() {
        // Search for a hero
        SearchResponse response = client.searchHero("spider-man");
        
        // Verify response
        assertNotNull(response);
        assertEquals("success", response.getResponse());
        assertEquals("spider-man", response.getResultsFor().toLowerCase());
        assertTrue(response.getResultsCount() > 0);
        
        // Print hero details
        var hero = response.getResults(0);
        System.out.println("\nHero: " + hero.getName());
        System.out.println("ID: " + hero.getId());
        System.out.println("Intelligence: " + hero.getPowerstats().getIntelligence());
        System.out.println("Full Name: " + hero.getBiography().getFullName());
        System.out.println("Publisher: " + hero.getBiography().getPublisher());
    }

    @Test
    void testSubscribeToUpdates() throws InterruptedException {
        // Create a list of heroes to monitor
        List<String> heroNames = List.of("spider-man");

        // Create an update handler
        SuperheroProxyClient.HeroUpdateHandler handler = new SuperheroProxyClient.HeroUpdateHandler() {
            @Override
            public void onUpdate(HeroUpdate update) {
                receivedUpdate = update;
                System.out.println("Received update for hero: " + update.getHero().getName());
                updateLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error in notification stream: " + t.getMessage());
                updateLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("Notification stream completed");
                updateLatch.countDown();
            }
        };

        // Subscribe to updates
        client.subscribeToUpdates(heroNames, handler);

        // Trigger a cache update by searching for the hero
        client.searchHero("spider-man");

        // Wait for the update with a timeout
        assertTrue(updateLatch.await(10, TimeUnit.SECONDS), "Did not receive update within timeout");
        assertNotNull(receivedUpdate, "Should have received an update");
        assertEquals("spider-man", receivedUpdate.getHero().getName().toLowerCase(), 
            "Update should be for spider-man");
    }
} 