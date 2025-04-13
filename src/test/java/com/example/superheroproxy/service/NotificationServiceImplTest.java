package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.utils.ResponseGenerator;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    private NotificationServiceImpl notificationService;
    private static final String MOCK_RESPONSE = "{\"response\":\"success\",\"id\":\"620\",\"name\":\"Spider-Man\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"55\",\"speed\":\"60\",\"durability\":\"75\",\"power\":\"74\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Peter Parker\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Spidey\",\"Wall-crawler\",\"Web-slinger\"],\"place-of-birth\":\"New York, New York\",\"first-appearance\":\"Amazing Fantasy #15\",\"publisher\":\"Marvel Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Human\",\"height\":[\"5'10\",\"178 cm\"],\"weight\":[\"165 lb\",\"75 kg\"],\"eye-color\":\"Hazel\",\"hair-color\":\"Brown\"},\"work\":{\"occupation\":\"Freelance photographer, teacher\",\"base\":\"New York, New York\"},\"connections\":{\"group-affiliation\":\"Avengers\",\"relatives\":\"Richard and Mary Parker (parents, deceased)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/133.jpg\"}}";

    @Mock
    private StreamObserver<HeroUpdate> responseObserver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notificationService = new NotificationServiceImpl();
    }

    @Test
    void testSubscribeToSpecificHero() throws Exception {
        // Create a subscription request for Spider-Man
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("620")
                .build();

        // Subscribe to updates
        notificationService.subscribeToUpdates(request, responseObserver);

        // Create a hero update
        Hero hero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        notificationService.notifyHeroUpdate("620", hero);

        // Verify that the observer received the update
        ArgumentCaptor<HeroUpdate> updateCaptor = ArgumentCaptor.forClass(HeroUpdate.class);
        verify(responseObserver).onNext(updateCaptor.capture());

        HeroUpdate update = updateCaptor.getValue();
        assertEquals("620", update.getHeroId());
        assertEquals(hero, update.getHero());
    }

    @Test
    void testSubscribeToAllHeroes() throws Exception {
        // Create a subscription request with no specific heroes (subscribe to all)
        SubscribeRequest request = SubscribeRequest.newBuilder().build();

        // Subscribe to updates
        notificationService.subscribeToUpdates(request, responseObserver);

        // Create a hero update
        Hero hero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        notificationService.notifyHeroUpdate("620", hero);

        // Verify that the observer received the update
        ArgumentCaptor<HeroUpdate> updateCaptor = ArgumentCaptor.forClass(HeroUpdate.class);
        verify(responseObserver).onNext(updateCaptor.capture());

        HeroUpdate update = updateCaptor.getValue();
        assertEquals("620", update.getHeroId());
        assertEquals(hero, update.getHero());
    }

    @Test
    void testMultipleSubscribers() throws Exception {
        // Create multiple subscribers
        StreamObserver<HeroUpdate> observer1 = mock(StreamObserver.class);
        StreamObserver<HeroUpdate> observer2 = mock(StreamObserver.class);

        // Subscribe both observers to Spider-Man updates
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("620")
                .build();

        notificationService.subscribeToUpdates(request, observer1);
        notificationService.subscribeToUpdates(request, observer2);

        // Create a hero update
        Hero hero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        notificationService.notifyHeroUpdate("620", hero);

        // Verify that both observers received the update
        verify(observer1).onNext(any(HeroUpdate.class));
        verify(observer2).onNext(any(HeroUpdate.class));
    }

    @Test
    void testSubscriberDisconnection() throws Exception {
        // Create a subscription request
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds("620")
                .build();

        // Subscribe to updates
        notificationService.subscribeToUpdates(request, responseObserver);

        // Simulate client disconnection
        notificationService.subscribeToUpdates(request, new StreamObserver<>() {
            @Override
            public void onNext(HeroUpdate value) {
                throw new RuntimeException("Client disconnected");
            }

            @Override
            public void onError(Throwable t) {
                // Expected error
            }

            @Override
            public void onCompleted() {
                // Expected completion
            }
        });

        // Create a hero update
        Hero hero = ResponseGenerator.generateHero(MOCK_RESPONSE);
        notificationService.notifyHeroUpdate("620", hero);

        // Verify that the disconnected subscriber was removed
        // and the remaining subscriber still received the update
        verify(responseObserver).onNext(any(HeroUpdate.class));
    }
} 