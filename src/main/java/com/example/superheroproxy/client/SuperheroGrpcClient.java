package com.example.superheroproxy.client;

import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SuperheroGrpcClient {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroGrpcClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final ManagedChannel channel;
    private final SuperheroServiceGrpc.SuperheroServiceStub superheroStub;
    private boolean isActive = true;

    public SuperheroGrpcClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .enableRetry()
                .maxRetryAttempts(3)
                .build();
        this.superheroStub = SuperheroServiceGrpc.newStub(channel);
    }

    public static void main(String[] args) throws InterruptedException {
        // Create client
        SuperheroGrpcClient client = new SuperheroGrpcClient("localhost", 9091);

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\nShutting down client...");
            try {
                client.shutdown();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
            }
        }));

        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Welcome to Superhero Search CLI!");
            System.out.println("Type 'exit' to quit");

            while (client.isActive) {
                System.out.print("\nEnter superhero name to search: ");
                String input = scanner.nextLine().trim();

                if ("exit".equalsIgnoreCase(input)) {
                    break;
                }

                if (input.isEmpty()) {
                    System.out.println("Please enter a valid superhero name");
                    continue;
                }

                client.searchSuperhero(input);
            }

            scanner.close();
        } catch (Exception e) {
            logger.error("Error in client: {}", e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    private void searchSuperhero(String name) {
        SearchRequest request = SearchRequest.newBuilder()
                .setName(name)
                .setPageSize(10)
                .setPageNumber(1)
                .build();

        final CountDownLatch latch = new CountDownLatch(1);

        searchHero(request, new io.grpc.stub.StreamObserver<>() {
            @Override
            public void onNext(SearchResponse response) {
                System.out.println("\nSearch Results for: " + response.getResultsFor());
                System.out.println("Total Results: " + response.getTotalCount());
                System.out.println("Page " + response.getCurrentPage() + " of " + response.getTotalPages());
                
                for (int i = 0; i < response.getResultsCount(); i++) {
                    var hero = response.getResults(i);
                    System.out.println("\nHero #" + (i + 1));
                    System.out.println("Name: " + hero.getName());
                    System.out.println("Full Name: " + hero.getBiography().getFullName());
                    System.out.println("Publisher: " + hero.getBiography().getPublisher());
                    System.out.println("Alignment: " + hero.getBiography().getAlignment());
                    System.out.println("Power Stats:");
                    System.out.println("  - Intelligence: " + hero.getPowerstats().getIntelligence());
                    System.out.println("  - Strength: " + hero.getPowerstats().getStrength());
                    System.out.println("  - Speed: " + hero.getPowerstats().getSpeed());
                    System.out.println("  - Durability: " + hero.getPowerstats().getDurability());
                    System.out.println("  - Power: " + hero.getPowerstats().getPower());
                    System.out.println("  - Combat: " + hero.getPowerstats().getCombat());
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error searching for superhero: " + t.getMessage());
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void searchHero(SearchRequest request, io.grpc.stub.StreamObserver<SearchResponse> responseObserver) {
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                superheroStub.searchHero(request, responseObserver);
                return;
            } catch (StatusRuntimeException e) {
                retryCount++;
                logger.warn("Failed to search hero (attempt {}/{}): {}", retryCount, MAX_RETRIES, e.getMessage());
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        responseObserver.onError(new RuntimeException("Failed to search hero after " + MAX_RETRIES + " attempts"));
    }

    public void shutdown() throws InterruptedException {
        isActive = false;
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
} 