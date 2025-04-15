package com.example.superheroproxy.client;

import com.example.superheroproxy.proto.NotificationServiceGrpc;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SuperheroGrpcClient {
    private static final Logger logger = LoggerFactory.getLogger(SuperheroGrpcClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public final ManagedChannel channel;
    private final SuperheroServiceGrpc.SuperheroServiceStub superheroStub;
    private final NotificationServiceGrpc.NotificationServiceStub notificationStub;

    public SuperheroGrpcClient(
            @Value("${grpc.client.channel.host}") String host,
            @Value("${grpc.client.channel.port}") int grpcPort,
            @Value("${grpc.client.channel.keep-alive.time}") int keepAliveTime,
            @Value("${grpc.client.channel.keep-alive.timeout}") int keepAliveTimeout,
            @Value("${grpc.client.channel.keep-alive.without-calls}") boolean keepAliveWithoutCalls) {
        this.channel = createChannel(host, grpcPort, keepAliveTime, keepAliveTimeout, keepAliveWithoutCalls);
        this.superheroStub = SuperheroServiceGrpc.newStub(channel);
        this.notificationStub = NotificationServiceGrpc.newStub(channel);
    }

    private ManagedChannel createChannel(String host, int port, int keepAliveTime, int keepAliveTimeout, boolean keepAliveWithoutCalls) {
        int retryCount = 0;
        ManagedChannel channel = null;
        
        while (retryCount < MAX_RETRIES) {
            try {
                channel = ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .keepAliveTime(keepAliveTime, TimeUnit.SECONDS)
                        .keepAliveTimeout(keepAliveTimeout, TimeUnit.SECONDS)
                        .keepAliveWithoutCalls(keepAliveWithoutCalls)
                        .build();
                
                // Test the connection
                channel.getState(true);
                return channel;
            } catch (Exception e) {
                retryCount++;
                logger.warn("Failed to create gRPC channel (attempt {}/{}): {}", retryCount, MAX_RETRIES, e.getMessage());
                
                if (channel != null) {
                    try {
                        channel.shutdownNow();
                    } catch (Exception ex) {
                        logger.error("Error shutting down failed channel: {}", ex.getMessage());
                    }
                }
                
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new RuntimeException("Failed to create gRPC channel after " + MAX_RETRIES + " attempts");
    }

    public void searchHero(SearchRequest request, io.grpc.stub.StreamObserver<SearchResponse> responseObserver) {
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                superheroStub.searchHero(request, responseObserver);
                return;
            } catch (StatusRuntimeException e) {
                retryCount++;
                logger.warn("Failed to search hero (attempt {}/{}): {}", retryCount, MAX_RETRIES, e.getMessage());
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        responseObserver.onError(new RuntimeException("Failed to search hero after " + MAX_RETRIES + " attempts"));
    }

    public void subscribeToUpdates(com.example.superheroproxy.proto.SubscribeRequest request, 
                                 io.grpc.stub.StreamObserver<com.example.superheroproxy.proto.HeroUpdate> responseObserver) {
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                notificationStub.subscribeToUpdates(request, responseObserver);
                return;
            } catch (StatusRuntimeException e) {
                retryCount++;
                logger.warn("Failed to subscribe to updates (attempt {}/{}): {}", retryCount, MAX_RETRIES, e.getMessage());
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        responseObserver.onError(new RuntimeException("Failed to subscribe to updates after " + MAX_RETRIES + " attempts"));
    }

    public void shutdown() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
} 