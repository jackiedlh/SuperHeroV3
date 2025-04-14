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
        SseEmitter emitter = new SseEmitter(0L); // No timeout
        emitters.put(heroId, emitter);
        AtomicBoolean isActive = new AtomicBoolean(true);

        // Send a ping every 30 seconds to keep the connection alive
        Thread pingThread = new Thread(() -> {
            while (isActive.get()) {
                try {
                    emitter.send("ping");
                    Thread.sleep(30000);
                } catch (Exception e) {
                    isActive.set(false);
                    break;
                }
            }
        });
        pingThread.setDaemon(true);
        pingThread.start();

        // Handle completion and timeout
        emitter.onCompletion(() -> {
            isActive.set(false);
            emitters.remove(heroId);
        });

        emitter.onTimeout(() -> {
            isActive.set(false);
            emitters.remove(heroId);
        });

        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addHeroIds(heroId)
                .build();

        asyncStub.subscribeToUpdates(request, new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                try {
                    emitter.send(convertHeroUpdateToMap(update));
                } catch (Exception e) {
                    isActive.set(false);
                    emitters.remove(heroId);
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                isActive.set(false);
                emitters.remove(heroId);
                emitter.completeWithError(t);
            }

            @Override
            public void onCompleted() {
                isActive.set(false);
                emitters.remove(heroId);
                emitter.complete();
            }
        });

        return emitter;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeAll() {
        SseEmitter emitter = new SseEmitter(0L); // No timeout
        emitters.put("all", emitter);
        AtomicBoolean isActive = new AtomicBoolean(true);

        // Send a ping every 30 seconds to keep the connection alive
        Thread pingThread = new Thread(() -> {
            while (isActive.get()) {
                try {
                    emitter.send("ping");
                    Thread.sleep(30000);
                } catch (Exception e) {
                    isActive.set(false);
                    break;
                }
            }
        });
        pingThread.setDaemon(true);
        pingThread.start();

        // Handle completion and timeout
        emitter.onCompletion(() -> {
            isActive.set(false);
            emitters.remove("all");
        });

        emitter.onTimeout(() -> {
            isActive.set(false);
            emitters.remove("all");
        });

        SubscribeRequest request = SubscribeRequest.newBuilder().build();

        asyncStub.subscribeToUpdates(request, new StreamObserver<HeroUpdate>() {
            @Override
            public void onNext(HeroUpdate update) {
                try {
                    emitter.send(convertHeroUpdateToMap(update));
                } catch (Exception e) {
                    isActive.set(false);
                    emitters.remove("all");
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                isActive.set(false);
                emitters.remove("all");
                emitter.completeWithError(t);
            }

            @Override
            public void onCompleted() {
                isActive.set(false);
                emitters.remove("all");
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