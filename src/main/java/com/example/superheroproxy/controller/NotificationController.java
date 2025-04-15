package com.example.superheroproxy.controller;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.utils.Converter;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller that handles real-time notifications using Server-Sent Events (SSE) and gRPC.
 * This controller allows clients to subscribe to hero updates and receive them in real-time.
 * It maintains a connection with a gRPC notification service and forwards updates to subscribed clients.
 * 
 * The controller implements a pub-sub pattern where:
 * - Clients subscribe to updates via SSE
 * - The controller maintains a gRPC connection to the notification service
 * - Updates are forwarded from gRPC to SSE clients
 * - A ping mechanism keeps SSE connections alive
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    /** Map to store active SSE emitters for each subscription (hero ID or "all") */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    /** gRPC channel for communication with the notification service */
    private final NotificationServiceGrpc.NotificationServiceStub asyncStub;

    @Autowired
    public NotificationController(SuperheroGrpcClient superheroGrpcClient) {
        this.asyncStub = NotificationServiceGrpc.newStub(superheroGrpcClient.channel);
    }

    /**
     * Subscribes a client to updates for a specific hero.
     * Creates an SSE connection that will receive real-time updates for the specified hero.
     *
     * @param heroId The ID of the hero to subscribe to
     * @return An SseEmitter that the client can use to receive updates
     */
    @GetMapping(value = "/subscribe/{heroId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String heroId) {
        return createSubscription(heroId, SubscribeRequest.newBuilder().addHeroIds(heroId).build());
    }

    /**
     * Subscribes a client to updates for all heroes.
     * Creates an SSE connection that will receive real-time updates for all heroes.
     *
     * @return An SseEmitter that the client can use to receive updates for all heroes
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeAll() {
        return createSubscription("all", SubscribeRequest.newBuilder().build());
    }

    /**
     * Unsubscribes a client from updates for a specific hero.
     * Removes the emitter from the map and completes it.
     *
     * @param heroId The ID of the hero to unsubscribe from
     */
    @PostMapping("/unsubscribe/{heroId}")
    public void unsubscribe(@PathVariable String heroId) {
        SseEmitter emitter = emitters.remove(heroId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    /**
     * Creates a new subscription with the specified ID and request.
     * Sets up the SSE emitter, ping thread, and gRPC subscription.
     *
     * @param subscriptionId The ID for this subscription (hero ID or "all")
     * @param request The gRPC subscription request
     * @return The configured SseEmitter
     */
    private SseEmitter createSubscription(String subscriptionId, SubscribeRequest request) {
        // Create a new SSE emitter with a 5-minute timeout
        SseEmitter emitter = new SseEmitter(300000L);
        emitters.put(subscriptionId, emitter);
        
        // Flags to track the connection state
        AtomicBoolean isActive = new AtomicBoolean(true);
        AtomicBoolean isCompleted = new AtomicBoolean(false);

        // Set up the ping thread to keep the connection alive
        Thread pingThread = startPingThread(emitter, subscriptionId, isActive, isCompleted);
        
        // Configure completion and timeout handlers
        setupEmitterHandlers(emitter, subscriptionId, isActive, isCompleted, pingThread);
        
        // Set up the gRPC subscription
        setupGrpcSubscription(emitter, subscriptionId, isActive, isCompleted, pingThread, request);

        return emitter;
    }

    /**
     * Creates and starts a background thread that sends periodic pings to keep the SSE connection alive.
     *
     * @param emitter The SSE emitter to send pings to
     * @param subscriptionId The ID of this subscription
     * @param isActive Flag indicating if the subscription is active
     * @param isCompleted Flag indicating if the subscription is completed
     * @return The started ping thread
     */
    private Thread startPingThread(SseEmitter emitter, String subscriptionId, 
                                 AtomicBoolean isActive, AtomicBoolean isCompleted) {
        Thread pingThread = new Thread(() -> {
            while (isActive.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (!isCompleted.get()) {
                        try {
                            emitter.send("ping");
                        } catch (IOException e) {
                            isActive.set(false);
                            break;
                        }
                    } else {
                        isActive.set(false);
                        break;
                    }
                    Thread.sleep(60000); // Send ping every 60 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in ping thread: " + e.getMessage());
                }
            }
            cleanupSubscription(subscriptionId, emitter, isActive, isCompleted);
        });
        pingThread.setDaemon(true);
        pingThread.start();
        return pingThread;
    }

    /**
     * Sets up the completion and timeout handlers for the SSE emitter.
     *
     * @param emitter The SSE emitter to configure
     * @param subscriptionId The ID of this subscription
     * @param isActive Flag indicating if the subscription is active
     * @param isCompleted Flag indicating if the subscription is completed
     * @param pingThread The ping thread to interrupt on completion/timeout
     */
    private void setupEmitterHandlers(SseEmitter emitter, String subscriptionId,
                                    AtomicBoolean isActive, AtomicBoolean isCompleted,
                                    Thread pingThread) {
        // Handle normal completion (client disconnection)
        emitter.onCompletion(() -> {
            isActive.set(false);
            isCompleted.set(true);
            emitters.remove(subscriptionId);
            pingThread.interrupt();
        });

        // Handle timeout
        emitter.onTimeout(() -> {
            isActive.set(false);
            isCompleted.set(true);
            emitters.remove(subscriptionId);
            pingThread.interrupt();
            emitter.complete();
        });
    }

    /**
     * Sets up the gRPC subscription and configures the StreamObserver to handle updates.
     *
     * @param emitter The SSE emitter to send updates to
     * @param subscriptionId The ID of this subscription
     * @param isActive Flag indicating if the subscription is active
     * @param isCompleted Flag indicating if the subscription is completed
     * @param pingThread The ping thread to interrupt on errors
     * @param request The gRPC subscription request
     */
    private void setupGrpcSubscription(SseEmitter emitter, String subscriptionId,
                                     AtomicBoolean isActive, AtomicBoolean isCompleted,
                                     Thread pingThread, SubscribeRequest request) {
        asyncStub.subscribeToUpdates(request, new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                if (!isActive.get() || isCompleted.get()) {
                    return;
                }
                try {
                    // Convert the gRPC update to a map and send it to the client
                    Map<String, Object> updateMap = Converter.convertHeroUpdateToMap(update);
                    emitter.send(updateMap, MediaType.APPLICATION_JSON);
                } catch (IOException e) {
                    handleError(e);
                } catch (Exception e) {
                    handleError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                handleError(t);
            }

            @Override
            public void onCompleted() {
                cleanupSubscription(subscriptionId, emitter, isActive, isCompleted);
                pingThread.interrupt();
            }

            /**
             * Handles errors from the gRPC stream or SSE emitter.
             * Cleans up resources and completes the emitter appropriately.
             *
             * @param t The error that occurred
             */
            private void handleError(Throwable t) {
                isActive.set(false);
                isCompleted.set(true);
                emitters.remove(subscriptionId);
                pingThread.interrupt();
                if (t instanceof Exception) {
                    emitter.completeWithError((Exception) t);
                } else {
                    emitter.complete();
                }
            }
        });
    }

    /**
     * Cleans up resources when a subscription ends.
     * Removes the emitter from the map and completes it if necessary.
     *
     * @param subscriptionId The ID of the subscription to clean up
     * @param emitter The SSE emitter to complete
     * @param isActive Flag indicating if the subscription is active
     * @param isCompleted Flag indicating if the subscription is completed
     */
    private void cleanupSubscription(String subscriptionId, SseEmitter emitter,
                                   AtomicBoolean isActive, AtomicBoolean isCompleted) {
        if (isActive.get()) {
            isActive.set(false);
            emitters.remove(subscriptionId);
            if (!isCompleted.get()) {
                emitter.complete();
            }
        }
    }
} 