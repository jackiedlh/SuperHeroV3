package com.example.superheroproxy.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.superheroproxy.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class NotificationService extends NotificationServiceGrpc.NotificationServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    // Map to store subscribers for each hero
    private final Map<String, List<StreamObserver<HeroUpdate>>> heroSubscribers = new ConcurrentHashMap<>();
    
    // List of all subscribers
    private final List<StreamObserver<HeroUpdate>> allSubscribers = new CopyOnWriteArrayList<>();

    @Override
    public void subscribeToUpdates(SubscribeRequest request, StreamObserver<HeroUpdate> responseObserver) {
        logger.info("New subscription request received");
        
        // Cast to ServerCallStreamObserver to handle cancellation
        ServerCallStreamObserver<HeroUpdate> serverCallStreamObserver = 
            (ServerCallStreamObserver<HeroUpdate>) responseObserver;
            
        // Set up cancellation handler
        serverCallStreamObserver.setOnCancelHandler(() -> {
            logger.info("Client cancelled the stream");
            removeSubscriber(responseObserver);
        });
        
        // Create a wrapper StreamObserver that handles client disconnection
        StreamObserver<HeroUpdate> wrappedObserver = new StreamObserver<>() {
            @Override
            public void onNext(HeroUpdate value) {
                if (!serverCallStreamObserver.isCancelled()) {
                    responseObserver.onNext(value);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.info("Client disconnected with error", t);
                removeSubscriber(responseObserver);
                if (!serverCallStreamObserver.isCancelled()) {
                    responseObserver.onError(t);
                }
            }

            @Override
            public void onCompleted() {
                logger.info("Client disconnected");
                removeSubscriber(responseObserver);
                if (!serverCallStreamObserver.isCancelled()) {
                    responseObserver.onCompleted();
                }
            }
        };
        
        // If specific heroes are requested, add to their subscriber lists
        if (!request.getHeroIdsList().isEmpty()) {
            for (String heroId : request.getHeroIdsList()) {
                heroSubscribers.computeIfAbsent(heroId, k -> new CopyOnWriteArrayList<>())
                        .add(wrappedObserver);
            }
        } else {
            // If no specific heroes requested, add to all subscribers
            allSubscribers.add(wrappedObserver);
        }
    }

    public void notifyHeroUpdate(String heroId, Hero hero, UpdateType updateType) {
        logger.debug("Notifying subscribers about {} update for hero ID: {}", updateType, heroId);
        
        HeroUpdate update = HeroUpdate.newBuilder()
                .setHeroId(heroId)
                .setHero(hero)
                .setUpdateType(updateType)
                .build();

        // Notify specific hero subscribers
        List<StreamObserver<HeroUpdate>> specificSubscribers = heroSubscribers.get(heroId);
        if (specificSubscribers != null) {
            specificSubscribers.forEach(subscriber -> {
                try {
                    if (subscriber instanceof ServerCallStreamObserver) {
                        if (!((ServerCallStreamObserver<HeroUpdate>) subscriber).isCancelled()) {
                            subscriber.onNext(update);
                        }
                    } else {
                        subscriber.onNext(update);
                    }
                } catch (Exception e) {
                    logger.error("Error sending update to subscriber", e);
                    removeSubscriber(subscriber);
                }
            });
        }

        // Notify all subscribers
        allSubscribers.forEach(subscriber -> {
            try {
                if (subscriber instanceof ServerCallStreamObserver) {
                    if (!((ServerCallStreamObserver<HeroUpdate>) subscriber).isCancelled()) {
                        subscriber.onNext(update);
                    }
                } else {
                    subscriber.onNext(update);
                }
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
        heroSubscribers.forEach((heroId, subscribers) -> subscribers.remove(subscriber));
    }
} 