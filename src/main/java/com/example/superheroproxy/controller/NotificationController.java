package com.example.superheroproxy.controller;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.PowerStats;
import com.example.superheroproxy.proto.Biography;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.io.IOException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceStub asyncStub;

    /**
     * This controller is only for test out notification grpc service
     * @param grpcPort
     */
    public NotificationController(@Value("${grpc.server.port}") int grpcPort) {
        this.channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        this.asyncStub = NotificationServiceGrpc.newStub(channel);
    }

    private Map<String, Object> convertHeroUpdateToMap(HeroUpdate update) {
        Map<String, Object> result = new HashMap<>();
        result.put("heroId", update.getHeroId());
        result.put("hero", convertHeroToMap(update.getHero()));
        result.put("updateType", update.getUpdateType().name());
        return result;
    }

    private Map<String, Object> convertHeroToMap(Hero hero) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", hero.getId());
        result.put("name", hero.getName());
        result.put("powerstats", convertPowerStatsToMap(hero.getPowerstats()));
        result.put("biography", convertBiographyToMap(hero.getBiography()));
        return result;
    }

    private Map<String, Object> convertPowerStatsToMap(PowerStats powerStats) {
        Map<String, Object> result = new HashMap<>();
        result.put("intelligence", powerStats.getIntelligence());
        result.put("strength", powerStats.getStrength());
        result.put("speed", powerStats.getSpeed());
        result.put("durability", powerStats.getDurability());
        result.put("power", powerStats.getPower());
        result.put("combat", powerStats.getCombat());
        return result;
    }

    private Map<String, Object> convertBiographyToMap(Biography biography) {
        Map<String, Object> result = new HashMap<>();
        result.put("fullName", biography.getFullName());
        result.put("alterEgos", biography.getAlterEgos());
        result.put("aliases", biography.getAliasesList());
        result.put("placeOfBirth", biography.getPlaceOfBirth());
        result.put("firstAppearance", biography.getFirstAppearance());
        result.put("publisher", biography.getPublisher());
        result.put("alignment", biography.getAlignment());
        return result;
    }

    @GetMapping(value = "/subscribe/{heroId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String heroId) {
        // Set a timeout of 5 minutes
        SseEmitter emitter = new SseEmitter(300000L);
        emitters.put(heroId, emitter);
        AtomicBoolean isActive = new AtomicBoolean(true);
        AtomicBoolean isCompleted = new AtomicBoolean(false);

        // Send a ping every 60 seconds to keep the connection alive
        Thread pingThread = new Thread(() -> {
            while (isActive.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (!isCompleted.get()) {
                        try {
                            emitter.send("ping");
                        } catch (IOException e) {
                            // Client disconnected, stop the ping thread
                            isActive.set(false);
                            break;
                        }
                    } else {
                        isActive.set(false);
                        break;
                    }
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Log other errors but continue
                    System.err.println("Error in ping thread: " + e.getMessage());
                }
            }
            // Clean up if thread exits
            if (isActive.get()) {
                isActive.set(false);
                emitters.remove(heroId);
                if (!isCompleted.get()) {
                    emitter.complete();
                }
            }
        });
        pingThread.setDaemon(true);
        pingThread.start();

        // Handle completion
        emitter.onCompletion(() -> {
            isActive.set(false);
            isCompleted.set(true);
            emitters.remove(heroId);
            pingThread.interrupt();
        });

        // Handle timeout
        emitter.onTimeout(() -> {
            isActive.set(false);
            isCompleted.set(true);
            emitters.remove(heroId);
            pingThread.interrupt();
            emitter.complete();
        });

        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds(heroId)
                .build();

        asyncStub.subscribeToUpdates(request, new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                if (!isActive.get() || isCompleted.get()) {
                    return;
                }
                try {
                    Map<String, Object> updateMap = convertHeroUpdateToMap(update);
                    emitter.send(updateMap, MediaType.APPLICATION_JSON);
                } catch (IOException e) {
                    // Client disconnected
                    isActive.set(false);
                    isCompleted.set(true);
                    emitters.remove(heroId);
                    pingThread.interrupt();
                    emitter.complete();
                } catch (Exception e) {
                    // Other errors
                    isActive.set(false);
                    isCompleted.set(true);
                    emitters.remove(heroId);
                    pingThread.interrupt();
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                isActive.set(false);
                isCompleted.set(true);
                emitters.remove(heroId);
                pingThread.interrupt();
                emitter.completeWithError(t);
            }

            @Override
            public void onCompleted() {
                isActive.set(false);
                isCompleted.set(true);
                emitters.remove(heroId);
                pingThread.interrupt();
                emitter.complete();
            }
        });

        return emitter;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeAll() {
        // Set a timeout of 5 minutes
        SseEmitter emitter = new SseEmitter(300000L);
        emitters.put("all", emitter);
        AtomicBoolean isActive = new AtomicBoolean(true);
        AtomicBoolean isCompleted = new AtomicBoolean(false);

        // Send a ping every 60 seconds to keep the connection alive
        Thread pingThread = new Thread(() -> {
            while (isActive.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (!isCompleted.get()) {
                        try {
                            emitter.send("ping");
                        } catch (IOException e) {
                            // Client disconnected, stop the ping thread
                            isActive.set(false);
                            break;
                        }
                    } else {
                        isActive.set(false);
                        break;
                    }
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Log other errors but continue
                    System.err.println("Error in ping thread: " + e.getMessage());
                }
            }
            // Clean up if thread exits
            if (isActive.get()) {
                isActive.set(false);
                emitters.remove("all");
                if (!isCompleted.get()) {
                    emitter.complete();
                }
            }
        });
        pingThread.setDaemon(true);
        pingThread.start();

        // Handle completion
        emitter.onCompletion(() -> {
            isActive.set(false);
            isCompleted.set(true);
            emitters.remove("all");
            pingThread.interrupt();
        });

        // Handle timeout
        emitter.onTimeout(() -> {
            isActive.set(false);
            isCompleted.set(true);
            emitters.remove("all");
            pingThread.interrupt();
            emitter.complete();
        });

        SubscribeRequest request = SubscribeRequest.newBuilder().build();

        asyncStub.subscribeToUpdates(request, new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                if (!isActive.get() || isCompleted.get()) {
                    return;
                }
                try {
                    Map<String, Object> updateMap = convertHeroUpdateToMap(update);
                    emitter.send(updateMap, MediaType.APPLICATION_JSON);
                } catch (IOException e) {
                    // Client disconnected
                    isActive.set(false);
                    isCompleted.set(true);
                    emitters.remove("all");
                    pingThread.interrupt();
                    emitter.complete();
                } catch (Exception e) {
                    // Other errors
                    isActive.set(false);
                    isCompleted.set(true);
                    emitters.remove("all");
                    pingThread.interrupt();
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                isActive.set(false);
                isCompleted.set(true);
                emitters.remove("all");
                pingThread.interrupt();
                emitter.completeWithError(t);
            }

            @Override
            public void onCompleted() {
                isActive.set(false);
                isCompleted.set(true);
                emitters.remove("all");
                pingThread.interrupt();
                emitter.complete();
            }
        });

        return emitter;
    }

    @PostMapping("/unsubscribe/{heroId}")
    public void unsubscribe(@PathVariable String heroId) {
        SseEmitter emitter = emitters.remove(heroId);
        if (emitter != null) {
            emitter.complete();
        }
    }
} 