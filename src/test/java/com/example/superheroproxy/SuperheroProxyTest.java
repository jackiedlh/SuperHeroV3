package com.example.superheroproxy;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest
@ActiveProfiles("test")
public class SuperheroProxyTest {

    @Value("${grpc.server.port}")
    private int grpcPort;

    @Autowired
    private CacheManager cacheManager;

    private ManagedChannel channel;
    private SuperheroServiceGrpc.SuperheroServiceBlockingStub blockingStub;

    @BeforeEach
    public void setup() {
        System.out.println("Connecting to gRPC server on port: " + grpcPort);
        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();
        
        blockingStub = SuperheroServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    public void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    public void testSearchHero() {
        try {
            // Create the request
            SearchRequest request = SearchRequest.newBuilder()
                    .setName("Batman")
                    .build();

            System.out.println("Sending request for hero: " + request.getName());
            
            // Make the RPC call
            SearchResponse response = blockingStub.searchHero(request);

            // Verify response
            assertNotNull(response);
            assertEquals("success", response.getResponse());
            assertEquals("Batman", response.getResultsFor());
            assertTrue(response.getResultsCount() > 0);

            // Verify cache hit for each hero by ID
            var cache = cacheManager.getCache("superheroCache");
            assertNotNull(cache);
            
            // Check that each hero in the response is cached by their ID
            for (Hero hero : response.getResultsList()) {
                assertNotNull(cache.get(hero.getId()), "Hero with ID " + hero.getId() + " should be cached");
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
        } catch (Exception e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
} 