package com.example.superheroproxy.client;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NotificationCmdClient {
    private static final Logger logger = LoggerFactory.getLogger(NotificationCmdClient.class);
    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceStub asyncStub;
    private StreamObserver<HeroUpdate> currentObserver;
    private CountDownLatch latch;
    private boolean isActive = true;

    public NotificationCmdClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        this.asyncStub = NotificationServiceGrpc.newStub(channel);
    }

    public void subscribeToUpdates(String... heroIds) {
        logger.info("Subscribing to updates for heroes: {}", Arrays.toString(heroIds));
        latch = new CountDownLatch(1);
        
        SubscribeRequest.Builder requestBuilder = SubscribeRequest.newBuilder();
        
        if (heroIds.length == 0) {
            // Subscribe to all heroes
            requestBuilder.setSubscribeAll(true);
            logger.info("Subscribing to updates for all heroes");
        } else {
            // Subscribe to specific heroes
            requestBuilder.addAllHeroIds(Arrays.asList(heroIds));
            logger.info("Subscribing to updates for specific heroes: {}", Arrays.toString(heroIds));
        }

        currentObserver = new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                if (!isActive) {
                    return;
                }
                logger.info("Received update for hero ID: {}", update.getHeroId());
                logger.info("hero name: {}", update.getHero().getName());

                logger.info("Stat {}", update.getUpdateType());
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in notification stream: {}", t.getMessage(), t);
                isActive = false;
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("Notification stream completed");
                isActive = false;
                latch.countDown();
            }
        };

        asyncStub.subscribeToUpdates(requestBuilder.build(), currentObserver);
    }

    public void disconnectNormally() {
        if (currentObserver != null) {
            logger.info("Disconnecting normally...");
            isActive = false;
            currentObserver.onCompleted();
        }
    }

    public void disconnectWithError() {
        if (currentObserver != null) {
            logger.info("Disconnecting with error...");
            isActive = false;
            currentObserver.onError(new RuntimeException("Test error disconnection"));
        }
    }

    public void simulateNetworkFailure() {
        if (channel != null) {
            logger.info("Simulating network failure...");
            isActive = false;
            channel.shutdownNow();
        }
    }

    public void shutdown() throws InterruptedException {
        isActive = false;
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
            logger.info("\nShutting down client...");
            try {
                client.shutdown();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
            }
        }));

        try {
            if (args.length == 0) {
                // Subscribe to all heroes if no arguments provided
                logger.info("Subscribing to updates for all heroes...");
                client.subscribeToUpdates();
            } else {
                // Subscribe to specific heroes provided as arguments
                logger.info("Subscribing to updates for heroes: {}", Arrays.toString(args));
                client.subscribeToUpdates(args);
            }

            // Keep the client running indefinitely
            logger.info("Client running continuously. Press Ctrl+C to exit.");
            
            // Wait indefinitely
            while (client.isActive) {
                Thread.sleep(1000);
            }

        } catch (InterruptedException e) {
            logger.info("Client interrupted. Shutting down...");
        } finally {
            client.shutdown();
        }
    }
} 