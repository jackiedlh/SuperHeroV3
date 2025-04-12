package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.proto.UpdateType;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@GrpcService
public class NotificationServiceImpl extends NotificationServiceGrpc.NotificationServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    // Map to store subscribers for each hero
    private final Map<String, List<StreamObserver<HeroUpdate>>> heroSubscribers = new ConcurrentHashMap<>();
    
    // List of all subscribers
    private final List<StreamObserver<HeroUpdate>> allSubscribers = new CopyOnWriteArrayList<>();

    @Override
    public void subscribeToUpdates(SubscribeRequest request, StreamObserver<HeroUpdate> responseObserver) {
        logger.info("New subscription request received");
        
        // Create a wrapper StreamObserver that handles client disconnection
        StreamObserver<HeroUpdate> wrappedObserver = new StreamObserver<>() {
            @Override
            public void onNext(HeroUpdate value) {
                responseObserver.onNext(value);
            }

            @Override
            public void onError(Throwable t) {
                logger.info("Client disconnected with error", t);
                removeSubscriber(responseObserver);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                logger.info("Client disconnected");
                removeSubscriber(responseObserver);
                responseObserver.onCompleted();
            }
        };
        
        // If specific heroes are requested, add to their subscriber lists
        if (!request.getHeroNamesList().isEmpty()) {
            for (String heroName : request.getHeroNamesList()) {
                heroSubscribers.computeIfAbsent(heroName.toLowerCase(), k -> new ArrayList<>())
                        .add(wrappedObserver);
            }
        } else {
            // If no specific heroes requested, add to all subscribers
            allSubscribers.add(wrappedObserver);
        }
    }

    public void notifyHeroUpdate(String heroName, Hero hero, UpdateType type) {
        logger.debug("Notifying subscribers about {} update for hero: {}", type, heroName);
        
        HeroUpdate update = HeroUpdate.newBuilder()
                .setType(type)
                .setHero(hero)
                .setTimestamp(System.currentTimeMillis())
                .build();

        // Notify specific hero subscribers
        List<StreamObserver<HeroUpdate>> specificSubscribers = heroSubscribers.get(heroName.toLowerCase());
        if (specificSubscribers != null) {
            specificSubscribers.forEach(subscriber -> {
                try {
                    subscriber.onNext(update);
                } catch (Exception e) {
                    logger.error("Error sending update to subscriber", e);
                    removeSubscriber(subscriber);
                }
            });
        }

        // Notify all subscribers
        allSubscribers.forEach(subscriber -> {
            try {
                subscriber.onNext(update);
            } catch (Exception e) {
                logger.error("Error sending update to subscriber", e);
                removeSubscriber(subscriber);
            }
        });
    }

    private void removeSubscriber(StreamObserver<HeroUpdate> subscriber) {
        // Remove from all subscribers
        allSubscribers.remove(subscriber);

        // Remove from specific hero subscribers
        heroSubscribers.forEach((heroName, subscribers) -> subscribers.remove(subscriber));
    }
} 