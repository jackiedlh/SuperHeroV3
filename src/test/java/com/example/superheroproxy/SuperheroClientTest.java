package com.example.superheroproxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.superheroproxy.proto.Biography;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.PowerStats;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public class SuperheroClientTest {

    @Value("${grpc.server.port}")
    private int grpcPort;

    private ManagedChannel channel;
    private SuperheroServiceGrpc.SuperheroServiceBlockingStub blockingStub;

    @BeforeEach
    public void setup() {
        System.out.println("Connecting to gRPC server on port: " + grpcPort);
        // Create a channel to the server
        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();
        
        // Create a blocking stub
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

            // Print the response
            System.out.println("Response: " + response.getResponse());
            System.out.println("Results for: " + response.getResultsFor());
            
            // Print each hero's details
            for (Hero hero : response.getResultsList()) {
                System.out.println("\nHero: " + hero.getName());
                System.out.println("ID: " + hero.getId());
                
                // Print power stats
                PowerStats powerStats = hero.getPowerstats();
                System.out.println("Power Stats:");
                System.out.println("  Intelligence: " + powerStats.getIntelligence());
                System.out.println("  Strength: " + powerStats.getStrength());
                System.out.println("  Speed: " + powerStats.getSpeed());
                
                // Print biography
                Biography bio = hero.getBiography();
                System.out.println("Biography:");
                System.out.println("  Full Name: " + bio.getFullName());
                System.out.println("  Publisher: " + bio.getPublisher());
                
                // Print image URL
                System.out.println("Image URL: " + hero.getImage().getUrl());
            }
        } catch (Exception e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
} 