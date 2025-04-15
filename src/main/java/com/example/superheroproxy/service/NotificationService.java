package com.example.superheroproxy.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.proto.UpdateType;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * A gRPC service that handles real-time notifications for hero updates.
 * This service allows clients to subscribe to updates for specific heroes or all heroes,
 * and notifies them when changes occur.
 * 
 * The service maintains two types of subscribers:
 * 1. Specific hero subscribers - clients interested in updates for particular heroes
 * 2. All subscribers - clients interested in updates for all heroes
 * 
 * Uses thread-safe collections (ConcurrentHashMap and CopyOnWriteArrayList) to handle
 * concurrent access from multiple clients.
 */
@GrpcService
public class NotificationService extends NotificationServiceGrpc.NotificationServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    // Map to store subscribers for each hero, keyed by hero ID
    private final Map<String, List<StreamObserver<HeroUpdate>>> heroSubscribers = new ConcurrentHashMap<>();
    
    // List of all subscribers who want updates for all heroes
    private final List<StreamObserver<HeroUpdate>> allSubscribers = new CopyOnWriteArrayList<>();

    /**
     * Handles client subscription requests for hero updates.
     * Clients can subscribe to updates for specific heroes or all heroes.
     * 
     * @param request The subscription request containing hero IDs to subscribe to
     * @param responseObserver The stream observer for sending updates back to the client
     */
    @Override
    public void subscribeToUpdates(SubscribeRequest request, StreamObserver<HeroUpdate> responseObserver) {
        logger.info("New subscription request received:" + request.toString());
        
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
                logger.error("Client disconnected with error", t);
//                removeSubscriber(responseObserver);
//                if (!serverCallStreamObserver.isCancelled()) {
//                    responseObserver.onError(t);
//                }
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
        if (request.getSubscribeAll()){
            // add to all subscribers
            allSubscribers.add(wrappedObserver);
            logger.info("add all subscribers:" + wrappedObserver.toString());
        }else if (!request.getHeroIdsList().isEmpty()) {
            for (String heroId : request.getHeroIdsList()) {
                heroSubscribers.computeIfAbsent(heroId, k -> new CopyOnWriteArrayList<>())
                        .add(wrappedObserver);
                logger.info("add {} subscribers: {}", heroId, wrappedObserver.toString());
            }
        }
    }

    /**
     * Notifies all relevant subscribers about a hero update.
     * 
     * @param heroId The ID of the hero that was updated
     * @param hero The updated hero object
     * @param updateType The type of update that occurred
     */
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

    /**
     * Removes a subscriber from both specific hero and all subscribers lists.
     * 
     * @param subscriber The subscriber to remove
     */
    private void removeSubscriber(StreamObserver<HeroUpdate> subscriber) {
        // Remove from all subscribers
        allSubscribers.remove(subscriber);

        // Remove from specific hero subscribers
        heroSubscribers.forEach((heroId, subscribers) -> subscribers.remove(subscriber));
    }
} 