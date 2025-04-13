package com.example.superheroproxy.controller;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;
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

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceStub asyncStub;

    public NotificationController(@Value("${grpc.server.port}") int grpcPort) {
        this.channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        this.asyncStub = NotificationServiceGrpc.newStub(channel);
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
                    emitter.send(update);
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

    @PostMapping("/unsubscribe/{heroId}")
    public void unsubscribe(@PathVariable String heroId) {
        SseEmitter emitter = emitters.remove(heroId);
        if (emitter != null) {
            emitter.complete();
        }
    }
} 