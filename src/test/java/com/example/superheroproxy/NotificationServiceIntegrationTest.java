package com.example.superheroproxy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.proto.UpdateType;
import com.example.superheroproxy.service.HeroCheckScheduleService;
import com.example.superheroproxy.service.NotificationService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

@SpringBootTest
@ActiveProfiles("test")
public class NotificationServiceIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceIntegrationTest.class);


    @Value("${grpc.server.port}")
    private int grpcPort;

    @Autowired
    private NotificationService notificationService;

    private ManagedChannel channel;
    private NotificationServiceGrpc.NotificationServiceStub asyncStub;

    @BeforeEach
    public void setup() {
        logger.info("Setting up gRPC channel on port: {}", grpcPort);
        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();
        asyncStub = NotificationServiceGrpc.newStub(channel);
    }

    @AfterEach
    public void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    public void testSubscriptionAndNotificationFlow() throws InterruptedException {
        // Create latches to track received notifications
        CountDownLatch userANotifications = new CountDownLatch(2);
        CountDownLatch userBNotifications = new CountDownLatch(1);
        
        // Track received updates
        List<HeroUpdate> userAUpdates = new ArrayList<>();
        List<HeroUpdate> userBUpdates = new ArrayList<>();

        // 1. User A subscribes to all hero updates
        logger.info("User A subscribing to all hero updates");
        SubscribeRequest userARequest = SubscribeRequest.newBuilder()
                .setSubscribeAll(true)
                .build();

        asyncStub.subscribeToUpdates(userARequest, new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                logger.info("User A received update for hero {}: {}", update.getHeroId(), update.getUpdateType());
                synchronized (userAUpdates) {
                    if (update.getHeroId().equals("619") || update.getHeroId().equals("620") ) {  //for we have a scedule job to sent nodification, we need ignore other notification
                        userAUpdates.add(update);
                        userANotifications.countDown();
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("User A subscription error", t);
            }

            @Override
            public void onCompleted() {
                logger.info("User A subscription completed");
            }
        });

        // 2. User B subscribes to specific heroes (620, 621)
        logger.info("User B subscribing to specific heroes: 620, 621");
        SubscribeRequest userBRequest = SubscribeRequest.newBuilder()
                .addHeroIds("620")
                .addHeroIds("621")
                .build();

        asyncStub.subscribeToUpdates(userBRequest, new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                logger.info("User B received update for hero {}: {}", update.getHeroId(), update.getUpdateType());
                synchronized (userBUpdates) {
                    userBUpdates.add(update);
                    userBNotifications.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("User B subscription error", t);
            }

            @Override
            public void onCompleted() {
                logger.info("User B subscription completed");
            }
        });

        // Wait for subscriptions to be established
        logger.info("Waiting for subscriptions to be established...");
        Thread.sleep(2000); // Increased wait time for subscription establishment

        // Clear any existing updates
        synchronized (userAUpdates) {
            userAUpdates.clear();
        }
        synchronized (userBUpdates) {
            userBUpdates.clear();
        }

        // 4. Send update for hero not subscribed by User B (619)
        logger.info("Sending update for hero 619 (not subscribed by User B)");
        Hero hero619 = Hero.newBuilder()
                .setId("619")
                .setName("Test Hero 619")
                .build();

        // Directly call the notification service to send the update
        notificationService.notifyHeroUpdate("619", hero619, UpdateType.UPDATED);
        logger.info("Notification sent for hero 619, waiting for delivery...");

        // Wait for notifications to be processed with increased timeout
        Thread.sleep(10000);
//        boolean notificationReceived = userANotifications.await(10, TimeUnit.SECONDS);
//
//        if (userANotifications.getCount() == 1){
//
//        }
//        logger.info("Notification wait completed. Received: {}", notificationReceived);
//
//        if (!notificationReceived) {
//            logger.error("User A did not receive notification within timeout period");
//            logger.error("Current userAUpdates size: {}", userAUpdates.size());
//            logger.error("Current userBUpdates size: {}", userBUpdates.size());
//        }

//        assertTrue(userANotifications.await(5, TimeUnit.SECONDS), "User A should receive notification for hero 619");
        synchronized (userAUpdates) {
            assertEquals(1, userAUpdates.size(), "User A should have received one update");
            assertEquals("619", userAUpdates.get(0).getHeroId(), "User A should have received update for hero 619");
        }
        synchronized (userBUpdates) {
            assertEquals(0, userBUpdates.size(), "User B should not have received any updates yet");
        }

        // 5. Send update for hero subscribed by User B (620)
        logger.info("Sending update for hero 620 (subscribed by User B)");
        Hero hero620 = Hero.newBuilder()
                .setId("620")
                .setName("Test Hero 620")
                .build();
        
        // Directly call the notification service to send the update
        notificationService.notifyHeroUpdate("620", hero620, UpdateType.UPDATED);

        // Wait for notifications to be processed
        assertTrue(userANotifications.await(5, TimeUnit.SECONDS), "User A should receive notification for hero 620");
        assertTrue(userBNotifications.await(5, TimeUnit.SECONDS), "User B should receive notification for hero 620");
        
        synchronized (userAUpdates) {
            assertEquals(2, userAUpdates.size(), "User A should have received two updates");
            assertEquals("620", userAUpdates.get(1).getHeroId(), "User A should have received update for hero 620");
        }
        synchronized (userBUpdates) {
            assertEquals(1, userBUpdates.size(), "User B should have received one update");
            assertEquals("620", userBUpdates.get(0).getHeroId(), "User B should have received update for hero 620");
        }

        // 6. Verify notification delivery
        logger.info("Verifying notification delivery");
        logger.info("User A received {} updates: {}", userAUpdates.size(), userAUpdates);
        logger.info("User B received {} updates: {}", userBUpdates.size(), userBUpdates);
        
        assertTrue(userAUpdates.stream().allMatch(update -> update != null), "All updates for User A should be valid");
        assertTrue(userBUpdates.stream().allMatch(update -> update != null), "All updates for User B should be valid");
    }
} 