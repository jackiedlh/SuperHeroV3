package com.example.superheroproxy.client;

import com.example.superheroproxy.proto.*;
import com.example.superheroproxy.service.SuperheroInnerService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SuperheroProxyClient {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroProxyClient.class);

    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceStub notificationStub;
    private final SuperheroInnerService superheroInnerService;

    @Autowired
    public SuperheroProxyClient(
            @Value("${grpc.server.host:localhost}") String host,
            @Value("${grpc.server.port:9091}") int port,
            SuperheroInnerService superheroInnerService) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.notificationStub = NotificationServiceGrpc.newStub(channel);
        this.superheroInnerService = superheroInnerService;
    }

    /**
     * Search for a hero by name
     * @param name The hero name to search for
     * @return SearchResponse containing the hero information
     */
    public SearchResponse searchHero(String name) {
        logger.info("Searching for hero: {}", name);
        return superheroInnerService.searchHero(name);
    }

    public Hero getHero(String id) {
        logger.info("Get hero: {}", id);
        return superheroInnerService.getHero(id);
    }

    /**
     * Subscribe to updates for specific heroes
     * @param heroIds List of hero IDs to subscribe to
     * @param updateHandler Handler for processing hero updates
     */
    public void subscribeToUpdates(List<String> heroIds, HeroUpdateHandler updateHandler) {
        logger.info("Subscribing to updates for hero IDs: {}", heroIds);
        
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addAllHeroIds(heroIds)
                .build();

        notificationStub.subscribeToUpdates(request, new StreamObserver<>() {
            @Override
            public void onNext(HeroUpdate update) {
                updateHandler.onUpdate(update);
            }

            @Override
            public void onError(Throwable t) {
                updateHandler.onError(t);
            }

            @Override
            public void onCompleted() {
                updateHandler.onCompleted();
            }
        });
    }

    /**
     * Shutdown the client and release resources
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Interface for handling hero updates
     */
    public interface HeroUpdateHandler {
        void onUpdate(HeroUpdate update);
        void onError(Throwable t);
        void onCompleted();
    }
} 