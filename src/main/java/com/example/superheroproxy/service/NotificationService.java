package com.example.superheroproxy.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import com.example.superheroproxy.config.NotificationConfig;
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
 * The service implements several features to handle high load and ensure reliability:
 * 1. Rate limiting to prevent overwhelming the service
 * 2. Asynchronous notification processing
 * 3. Resource cleanup for inactive subscribers
 * 4. Thread-safe subscriber management
 * 5. Configurable limits for subscribers
 * 
 * @GrpcService annotation marks this class as a gRPC service implementation
 */
@GrpcService
public class NotificationService extends NotificationServiceGrpc.NotificationServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    private final NotificationConfig config;
    private final Executor asyncExecutor;

    // Thread pools for handling asynchronous operations
    /** Scheduled executor for periodic cleanup tasks */
    private final ScheduledExecutorService cleanupExecutor;
    
    // Rate limiting and synchronization
    /** Map to track notification rates per hero for rate limiting */
    private final Map<String, AtomicInteger> notificationRates = new ConcurrentHashMap<>();
    /** Lock for rate limiting operations to ensure thread safety */
    private final ReentrantReadWriteLock rateLimitLock = new ReentrantReadWriteLock();
    
    // Subscriber management
    /** Map of hero-specific subscribers, keyed by hero ID */
    private final Map<String, List<SubscriberInfo>> heroSubscribers = new ConcurrentHashMap<>();
    /** List of subscribers interested in all hero updates */
    private final List<SubscriberInfo> allSubscribers = new CopyOnWriteArrayList<>();
    /** Counter for total number of active subscribers */
    private final AtomicInteger totalSubscribers = new AtomicInteger(0);
    private volatile boolean isShuttingDown = false;

    /**
     * Inner class to track subscriber information including last activity time.
     * This helps in identifying and cleaning up inactive subscribers.
     */
    private static class SubscriberInfo {
        /** The actual gRPC stream observer for sending updates */
        final StreamObserver<HeroUpdate> observer;
        /** Timestamp of the last activity from this subscriber */
        final long lastActivityTime;
        
        SubscriberInfo(StreamObserver<HeroUpdate> observer) {
            this.observer = observer;
            this.lastActivityTime = System.currentTimeMillis();
        }
    }

    @Autowired
    public NotificationService(NotificationConfig config, AsyncConfigurer asyncConfigurer) {
        this.config = config;
        this.asyncExecutor = asyncConfigurer.getAsyncExecutor();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupInactiveSubscribers,
            config.getCleanupIntervalMinutes(),
            config.getCleanupIntervalMinutes(),
            TimeUnit.MINUTES
        );
    }

    /**
     * Handles client subscription requests for hero updates.
     * Clients can subscribe to updates for specific heroes or all heroes.
     * Implements limits on the number of subscribers to prevent resource exhaustion.
     * 
     * @param request The subscription request containing hero IDs to subscribe to
     * @param responseObserver The stream observer for sending updates back to the client
     */
    @Override
    public void subscribeToUpdates(SubscribeRequest request, StreamObserver<HeroUpdate> responseObserver) {
        // Check if maximum total subscribers limit is reached
        if (totalSubscribers.get() >= config.getMaxTotalSubscribers()) {
            responseObserver.onError(new RuntimeException("Maximum number of subscribers reached"));
            return;
        }

        logger.info("New subscription request received: {}", request);
        
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

        // Create subscriber info with current timestamp
        SubscriberInfo subscriberInfo = new SubscriberInfo(wrappedObserver);
        
        if (request.getSubscribeAll()) {
            // Add to all subscribers list
            allSubscribers.add(subscriberInfo);
            totalSubscribers.incrementAndGet();
            logger.info("Added all subscribers: {}", wrappedObserver.hashCode());
        } else if (!request.getHeroIdsList().isEmpty()) {
            // Add to specific hero subscriber lists
            for (String heroId : request.getHeroIdsList()) {
                List<SubscriberInfo> subscribers = heroSubscribers.computeIfAbsent(
                    heroId, 
                    k -> new CopyOnWriteArrayList<>()
                );
                
                // Check per-hero subscriber limit
                if (subscribers.size() >= config.getMaxSubscribersPerHero()) {
                    logger.warn("Maximum subscribers reached for hero: {}", heroId);
                    continue;
                }
                
                subscribers.add(subscriberInfo);
                totalSubscribers.incrementAndGet();
                logger.info("Added {} subscribers: {}", heroId, wrappedObserver.hashCode());
            }
        }
    }

    /**
     * Notifies subscribers about a hero update.
     * Implements rate limiting and processes notifications asynchronously.
     * 
     * @param heroId The ID of the hero that was updated
     * @param hero The updated hero object
     * @param updateType The type of update that occurred
     */
    public void notifyHeroUpdate(String heroId, Hero hero, UpdateType updateType) {
        // Check if service is shutting down
        if (isShuttingDown) {
            logger.warn("Service is shutting down, ignoring update for hero: {}", heroId);
            return;
        }

        // Check rate limit before processing
        if (!checkRateLimit(heroId)) {
            logger.warn("Rate limit exceeded for hero: {}", heroId);
            return;
        }

        logger.debug("Notifying subscribers about {} update for hero ID: {}", updateType, heroId);
        
        // Build the update message
        HeroUpdate update = HeroUpdate.newBuilder()
                .setHeroId(heroId)
                .setHero(hero)
                .setUpdateType(updateType)
                .build();

        // Process notifications asynchronously
        try {
            asyncExecutor.execute(() -> {
                try {
                    CompletableFuture<Void> specificSubscribersFuture = CompletableFuture.runAsync(() -> 
                        notifySpecificSubscribers(heroId, update), asyncExecutor);
                    
                    CompletableFuture<Void> allSubscribersFuture = CompletableFuture.runAsync(() -> 
                        notifyAllSubscribers(update), asyncExecutor);
                    
                    // Wait for both notification tasks to complete
                    CompletableFuture.allOf(specificSubscribersFuture, allSubscribersFuture).join();
                } catch (Exception e) {
                    logger.error("Error processing notifications", e);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.warn("Failed to submit notification task for hero: {} - service may be shutting down", heroId);
        }
    }

    /**
     * Notifies subscribers who are specifically interested in updates for a particular hero.
     * 
     * @param heroId The ID of the hero that was updated
     * @param update The update message to send
     */
    private void notifySpecificSubscribers(String heroId, HeroUpdate update) {
        List<SubscriberInfo> specificSubscribers = heroSubscribers.get(heroId);
        if (specificSubscribers != null) {
            specificSubscribers.forEach(subscriberInfo -> {
                try {
                    StreamObserver<HeroUpdate> subscriber = subscriberInfo.observer;
                    if (subscriber instanceof ServerCallStreamObserver) {
                        if (!((ServerCallStreamObserver<HeroUpdate>) subscriber).isCancelled()) {
                            subscriber.onNext(update);
                        } else {
                            logger.warn("One of specificSubscribers for {} cancelled", heroId);
                        }
                    } else {
                        subscriber.onNext(update);
                    }
                } catch (Exception e) {
                    logger.error("Error sending update to subscriber", e);
                    removeSubscriber(subscriberInfo.observer);
                }
            });
        }
    }

    /**
     * Notifies all subscribers who are interested in updates for any hero.
     * 
     * @param update The update message to send
     */
    private void notifyAllSubscribers(HeroUpdate update) {
        allSubscribers.forEach(subscriberInfo -> {
            try {
                StreamObserver<HeroUpdate> subscriber = subscriberInfo.observer;
                if (subscriber instanceof ServerCallStreamObserver) {
                    if (!((ServerCallStreamObserver<HeroUpdate>) subscriber).isCancelled()) {
                        subscriber.onNext(update);
                    } else {
                        logger.warn("One of allSubscribers cancelled");
                    }
                } else {
                    subscriber.onNext(update);
                }
            } catch (Exception e) {
                logger.error("Error sending update to subscriber", e);
                removeSubscriber(subscriberInfo.observer);
            }
        });
    }

    /**
     * Checks if the rate limit for a hero has been exceeded.
     * Uses a read-write lock for thread-safe rate limiting.
     * 
     * @param heroId The ID of the hero to check rate limit for
     * @return true if the rate limit has not been exceeded, false otherwise
     */
    private boolean checkRateLimit(String heroId) {
        rateLimitLock.readLock().lock();
        try {
            AtomicInteger rateCounter = notificationRates.computeIfAbsent(
                heroId, 
                k -> new AtomicInteger(0)
            );
            return rateCounter.incrementAndGet() <= config.getRateLimitPerMinute();
        } finally {
            rateLimitLock.readLock().unlock();
        }
    }

    /**
     * Cleans up inactive subscribers and resets rate limit counters.
     * Removes subscribers who haven't been active for the timeout period.
     */
    private void cleanupInactiveSubscribers() {
        long cutoffTime = System.currentTimeMillis() - (config.getSubscriberTimeoutMinutes() * 60 * 1000);
        
        // Clean up all subscribers
        allSubscribers.removeIf(subscriberInfo -> 
            subscriberInfo.lastActivityTime < cutoffTime
        );
        
        // Clean up hero-specific subscribers
        heroSubscribers.forEach((heroId, subscribers) -> {
            subscribers.removeIf(subscriberInfo -> 
                subscriberInfo.lastActivityTime < cutoffTime
            );
            if (subscribers.isEmpty()) {
                heroSubscribers.remove(heroId);
            }
        });
        
        // Reset rate limit counters
        notificationRates.clear();
    }

    /**
     * Removes a subscriber from both specific hero and all subscribers lists.
     * 
     * @param subscriber The subscriber to remove
     */
    private void removeSubscriber(StreamObserver<HeroUpdate> subscriber) {
        allSubscribers.removeIf(info -> info.observer == subscriber);
        heroSubscribers.forEach((heroId, subscribers) -> 
            subscribers.removeIf(info -> info.observer == subscriber)
        );
        totalSubscribers.decrementAndGet();
    }

    /**
     * Cleanup method called when the application is shutting down.
     * Properly shuts down all thread pools and resources.
     * Annotated with @PreDestroy to ensure it's called during application shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up NotificationService resources");
        isShuttingDown = true;
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while shutting down NotificationService", e);
        }
    }
} 