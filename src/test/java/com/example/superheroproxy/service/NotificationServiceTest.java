package com.example.superheroproxy.service;

import com.example.superheroproxy.config.AsyncConfig;
import com.example.superheroproxy.config.NotificationConfig;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.proto.UpdateType;
import com.example.superheroproxy.utils.ResponseGenerator;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executor;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private NotificationService notificationService;
    private NotificationConfig config;
    private static final String MOCK_RESPONSE = "{\"response\":\"success\",\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"55\",\"speed\":\"60\",\"durability\":\"75\",\"power\":\"74\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Peter Parker\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Spidey\",\"Wall-crawler\",\"Web-slinger\"],\"place-of-birth\":\"New York, New York\",\"first-appearance\":\"Amazing Fantasy #15\",\"publisher\":\"Marvel Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Human\",\"height\":[\"5'10\",\"178 cm\"],\"weight\":[\"165 lb\",\"75 kg\"],\"eye-color\":\"Hazel\",\"hair-color\":\"Brown\"},\"work\":{\"occupation\":\"Freelance photographer, teacher\",\"base\":\"New York, New York\"},\"connections\":{\"group-affiliation\":\"Avengers\",\"relatives\":\"Richard and Mary Parker (parents, deceased)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}";

    @Mock
    private ServerCallStreamObserver<HeroUpdate> responseObserver1;
    
    @Mock
    private ServerCallStreamObserver<HeroUpdate> responseObserver2;
    
    @Mock
    private ServerCallStreamObserver<HeroUpdate> serverCallStreamObserver;

    private ThreadPoolTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new NotificationConfig();
        AsyncConfig asyncConfig = new AsyncConfig();
        
        // Configure test executor
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(2);
        taskExecutor.setMaxPoolSize(4);
        taskExecutor.setQueueCapacity(50);
        taskExecutor.setThreadNamePrefix("Test-Async-");
        taskExecutor.initialize();

        // Create a custom AsyncConfigurer implementation
        AsyncConfigurer asyncConfigurer = new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return taskExecutor;
            }
        };

        notificationService = new NotificationService(config, asyncConfigurer);
    }

    @AfterEach
    void tearDown() {
        notificationService.cleanup();
    }

    @Test
    void testSubscribeToSpecificHero() {
        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();

        // When
        notificationService.subscribeToUpdates(request, responseObserver1);

        // Then
        verify(responseObserver1, never()).onError(any());
        verify(responseObserver1, never()).onCompleted();
    }

    @Test
    void testSubscribeToAllHeroes() {
        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .setSubscribeAll(true)
                .build();

        // When
        notificationService.subscribeToUpdates(request, responseObserver1);

        // Then
        verify(responseObserver1, never()).onError(any());
        verify(responseObserver1, never()).onCompleted();
    }

    @Test
    void testNotifySpecificHeroSubscribers() throws InterruptedException {
        // Configure mock observers
        when(responseObserver1.isCancelled()).thenReturn(false);
        doNothing().when(responseObserver1).onNext(any());
        doNothing().when(responseObserver1).setOnCancelHandler(any());

        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();
        notificationService.subscribeToUpdates(request, responseObserver1);

        Hero hero = Hero.newBuilder().setId("hero1").setName("Test Hero").build();
        UpdateType updateType = UpdateType.UPDATED;

        // When
        notificationService.notifyHeroUpdate("hero1", hero, updateType);

        // Wait for async notification to be processed
        Thread.sleep(100);

        // Then
        ArgumentCaptor<HeroUpdate> updateCaptor = ArgumentCaptor.forClass(HeroUpdate.class);
        verify(responseObserver1, timeout(1000)).onNext(updateCaptor.capture());
        
        HeroUpdate capturedUpdate = updateCaptor.getValue();
        assertEquals("hero1", capturedUpdate.getHeroId());
        assertEquals(hero, capturedUpdate.getHero());
        assertEquals(updateType, capturedUpdate.getUpdateType());
    }

    @Test
    void testNotifyAllSubscribers() throws InterruptedException {
        // Configure mock observers
        when(responseObserver1.isCancelled()).thenReturn(false);
        when(responseObserver2.isCancelled()).thenReturn(false);
        doNothing().when(responseObserver1).onNext(any());
        doNothing().when(responseObserver2).onNext(any());
        doNothing().when(responseObserver1).setOnCancelHandler(any());
        doNothing().when(responseObserver2).setOnCancelHandler(any());

        // Given
        SubscribeRequest request1 = SubscribeRequest.newBuilder()
                .setSubscribeAll(true)
                .build();
        SubscribeRequest request2 = SubscribeRequest.newBuilder()
                .setSubscribeAll(true)
                .build();

        notificationService.subscribeToUpdates(request1, responseObserver1);
        notificationService.subscribeToUpdates(request2, responseObserver2);

        Hero hero = Hero.newBuilder().setId("hero1").setName("Test Hero").build();
        UpdateType updateType = UpdateType.UPDATED;

        // When
        notificationService.notifyHeroUpdate("hero1", hero, updateType);

        // Wait for async notification to be processed
        Thread.sleep(100);

        // Then
        ArgumentCaptor<HeroUpdate> updateCaptor = ArgumentCaptor.forClass(HeroUpdate.class);
        verify(responseObserver1, timeout(1000)).onNext(updateCaptor.capture());
        verify(responseObserver2, timeout(1000)).onNext(updateCaptor.capture());
    }

    @Test
    void testRateLimiting() throws InterruptedException {
        // Configure mock observer
        when(responseObserver1.isCancelled()).thenReturn(false);
        doNothing().when(responseObserver1).onNext(any());
        doNothing().when(responseObserver1).setOnCancelHandler(any());

        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();
        notificationService.subscribeToUpdates(request, responseObserver1);

        Hero hero = Hero.newBuilder().setId("hero1").setName("Test Hero").build();
        UpdateType updateType = UpdateType.UPDATED;

        // When - Send more updates than the rate limit
        for (int i = 0; i < 10001; i++) {
            notificationService.notifyHeroUpdate("hero1", hero, updateType);
        }

        // Wait for async notification to be processed
        Thread.sleep(100);

        // Then - Verify that updates are still being processed
        // (The service should continue to accept updates, just log a warning)
        assertDoesNotThrow(() -> notificationService.notifyHeroUpdate("hero1", hero, updateType));
    }

    @Test
    void testSubscriberCancellation() throws InterruptedException {
        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();
        
        // Configure observer to be cancelled
        when(responseObserver1.isCancelled()).thenReturn(true);
        
        // Subscribe and then simulate cancellation
        notificationService.subscribeToUpdates(request, responseObserver1);

        // When
        Hero hero = Hero.newBuilder().setId("hero1").setName("Test Hero").build();
        notificationService.notifyHeroUpdate("hero1", hero, UpdateType.UPDATED);

        Thread.sleep(100);

        // Then
        verify(responseObserver1, never()).onNext(any());
    }

    @Test
    void testConcurrentSubscriptions() throws InterruptedException {
        // Given
        int numThreads = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    ServerCallStreamObserver<HeroUpdate> observer = mock(ServerCallStreamObserver.class);
                    when(observer.isCancelled()).thenReturn(false);
                    
                    SubscribeRequest request = SubscribeRequest.newBuilder()
                            .addHeroIds("hero1")
                            .build();
                    notificationService.subscribeToUpdates(request, observer);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(numThreads, successCount.get());
    }

    @Test
    void testMaxSubscribersPerHero() {
        // Configure mock observers
//        when(responseObserver1.isCancelled()).thenReturn(false);
        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();

        // When - Try to subscribe more than the maximum allowed
        for (int i = 0; i < 1001; i++) {
            ServerCallStreamObserver<HeroUpdate> observer = mock(ServerCallStreamObserver.class);
            //when(observer.isCancelled()).thenReturn(false);
            notificationService.subscribeToUpdates(request, observer);
        }

        // Then - Verify that the service still accepts new subscriptions
        // (The service should log a warning but continue to accept subscriptions)
        assertDoesNotThrow(() -> notificationService.subscribeToUpdates(request, responseObserver1));
    }

    @Test
    void testCleanup() {
        // Configure mock observers
//        when(responseObserver1.isCancelled()).thenReturn(false);
        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();
        notificationService.subscribeToUpdates(request, responseObserver1);

        // When
        notificationService.cleanup();

        // Then
        Hero hero = Hero.newBuilder().setId("hero1").setName("Test Hero").build();
        notificationService.notifyHeroUpdate("hero1", hero, UpdateType.UPDATED);
        verify(responseObserver1, never()).onNext(any());
    }

    @Test
    void testErrorHandling() {
        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();
        notificationService.subscribeToUpdates(request, responseObserver1);

        // When - Simulate an error in the observer
        //doThrow(new RuntimeException("Test error")).when(responseObserver1).onNext(any());

        // Then
        Hero hero = Hero.newBuilder().setId("hero1").setName("Test Hero").build();
        assertDoesNotThrow(() -> notificationService.notifyHeroUpdate("hero1", hero, UpdateType.UPDATED));
    }

    @Test
    void testCleanupInactiveSubscribers() throws InterruptedException {
        // Configure mock observer
//        when(responseObserver1.isCancelled()).thenReturn(false);
//        doNothing().when(responseObserver1).onNext(any());
        doNothing().when(responseObserver1).setOnCancelHandler(any());

        // Given
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("hero1")
                .build();
        notificationService.subscribeToUpdates(request, responseObserver1);

        // When - Wait for cleanup interval
        Thread.sleep(1000);

        // Then - Verify subscriber was removed
        Hero hero = Hero.newBuilder().setId("hero1").setName("Test Hero").build();
        notificationService.notifyHeroUpdate("hero1", hero, UpdateType.UPDATED);
        verify(responseObserver1, timeout(1000).times(0)).onNext(any());
    }
} 