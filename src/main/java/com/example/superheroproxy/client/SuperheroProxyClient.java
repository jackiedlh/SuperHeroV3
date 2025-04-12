package com.example.superheroproxy.client;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SubscribeRequest;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SuperheroProxyClient {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroProxyClient.class);

    private final ManagedChannel channel;
    private final SuperheroServiceGrpc.SuperheroServiceBlockingStub searchStub;
    private final NotificationServiceGrpc.NotificationServiceStub notificationStub;

    public SuperheroProxyClient(
            @Value("${grpc.server.host:localhost}") String host,
            @Value("${grpc.server.port:9091}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.searchStub = SuperheroServiceGrpc.newBlockingStub(channel);
        this.notificationStub = NotificationServiceGrpc.newStub(channel);
    }

    /**
     * Search for a hero by name
     * @param name The hero name to search for
     * @return SearchResponse containing the hero information
     */
    public SearchResponse searchHero(String name) {
        logger.info("Searching for hero: {}", name);
        SearchRequest request = SearchRequest.newBuilder()
                .setName(name)
                .build();
        return searchStub.searchHero(request);
    }

    /**
     * Subscribe to updates for specific heroes
     * @param heroNames List of hero names to subscribe to
     * @param updateHandler Handler for processing hero updates
     */
    public void subscribeToUpdates(List<String> heroNames, HeroUpdateHandler updateHandler) {
        logger.info("Subscribing to updates for heroes: {}", heroNames);
        
        SubscribeRequest request = SubscribeRequest.newBuilder()
                .addAllHeroNames(heroNames)
                .build();

        notificationStub.subscribeToUpdates(request, new StreamObserver<HeroUpdate>() {
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