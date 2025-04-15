package com.example.superheroproxy.controller;

import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.proto.SuperheroServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SuperheroGrpcClient {
    public final ManagedChannel channel;


    public SuperheroGrpcClient(
            @Value("${grpc.client.channel.host}") String host,
            @Value("${grpc.client.channel.port}") int grpcPort,
            @Value("${grpc.client.channel.keep-alive.time}") int keepAliveTime,
            @Value("${grpc.client.channel.keep-alive.timeout}") int keepAliveTimeout,
            @Value("${grpc.client.channel.keep-alive.without-calls}") boolean keepAliveWithoutCalls) {
        this.channel = ManagedChannelBuilder.forAddress(host, grpcPort)
                .usePlaintext()
                .keepAliveTime(keepAliveTime, TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeout, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(keepAliveWithoutCalls)
                .build();

    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
} 