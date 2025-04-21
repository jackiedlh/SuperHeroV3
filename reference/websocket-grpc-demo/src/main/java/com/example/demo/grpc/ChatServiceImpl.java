package com.example.demo.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {

    private final ConcurrentHashMap<String, StreamObserver<ChatProto.ChatMessage>> observers = new ConcurrentHashMap<>();

    @Override
    public void sendMessage(ChatProto.ChatMessage request, StreamObserver<ChatProto.ChatResponse> responseObserver) {
        log.info("Received message from {}: {}", request.getSender(), request.getContent());
        
        // Broadcast message to all connected clients
        observers.values().forEach(observer -> {
            try {
                observer.onNext(request);
            } catch (Exception e) {
                log.error("Error sending message to client", e);
            }
        });

        ChatProto.ChatResponse response = ChatProto.ChatResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Message received and broadcasted")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void streamMessages(ChatProto.Empty request, StreamObserver<ChatProto.ChatMessage> responseObserver) {
        String clientId = String.valueOf(responseObserver.hashCode());
        observers.put(clientId, responseObserver);
        
        log.info("New client connected: {}", clientId);

        // Clean up when client disconnects
        responseObserver.setOnCancelHandler(() -> {
            observers.remove(clientId);
            log.info("Client disconnected: {}", clientId);
        });
    }
} 