package com.example.superheroproxy.client;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotificationCmdClient {
    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceStub asyncStub;
    private StreamObserver<HeroUpdate> currentObserver;
    private CountDownLatch latch;

    public NotificationCmdClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = NotificationServiceGrpc.newStub(channel);
    }

    public void subscribeToUpdates(String... heroIds) {
        System.out.println("Subscribing to updates for heroes: " + Arrays.toString(heroIds));
        latch = new CountDownLatch(1);
        
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addAllHeroIds(Arrays.asList(heroIds))
                .build();

        currentObserver = new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                System.out.println("\nReceived update for hero: " + update.getHero().getName());
                System.out.println("Hero ID: " + update.getHeroId());
                System.out.println("Power Stats: " + update.getHero().getPowerstats());
                System.out.println("Biography: " + update.getHero().getBiography());
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

        asyncStub.subscribeToUpdates(request, currentObserver);
    }

    // Test normal disconnection
    public void disconnectNormally() {
        if (currentObserver != null) {
            System.out.println("Disconnecting normally...");
            currentObserver.onCompleted();
        }
    }

    // Test error disconnection
    public void disconnectWithError() {
        if (currentObserver != null) {
            System.out.println("Disconnecting with error...");
            currentObserver.onError(new RuntimeException("Test error disconnection"));
        }
    }

    // Test network disconnection simulation
    public void simulateNetworkFailure() {
        if (channel != null) {
            System.out.println("Simulating network failure...");
            channel.shutdownNow();
        }
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void waitForDisconnection() throws InterruptedException {
        if (latch != null) {
            latch.await(10, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Create client
        NotificationCmdClient client = new NotificationCmdClient("localhost", 9091);

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down client...");
            try {
                client.shutdown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        try {
            // Subscribe to specific heroes (e.g., Spider-Man)
            client.subscribeToUpdates("620");

            // Keep the client running indefinitely
            System.out.println("Client running continuously. Press Ctrl+C to exit.");
            
            // Wait indefinitely
            while (true) {
                Thread.sleep(1000);
            }

        } catch (InterruptedException e) {
            System.out.println("Client interrupted. Shutting down...");
        } finally {
            client.shutdown();
        }
    }
} 