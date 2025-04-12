package com.example.superheroproxy.client;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SubscribeRequest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

@Component
public class NotificationClient {
    private static final Logger logger = LoggerFactory.getLogger(NotificationClient.class);

    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceStub asyncStub;

    public NotificationClient(
            @Value("${grpc.server.host:localhost}") String host,
            @Value("${grpc.server.port:9090}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.asyncStub = NotificationServiceGrpc.newStub(channel);
    }

    public void subscribeToUpdates(List<String> heroNames, StreamObserver<HeroUpdate> responseObserver) {
        logger.info("Subscribing to updates for heroes: {}", heroNames);
        
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addAllHeroNames(heroNames)
                .build();

        asyncStub.subscribeToUpdates(request, responseObserver);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
} 