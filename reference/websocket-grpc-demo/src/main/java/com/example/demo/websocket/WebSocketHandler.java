package com.example.demo.websocket;

import com.example.demo.grpc.ChatProto;
import com.example.demo.grpc.ChatServiceGrpc;
import com.google.gson.Gson;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final ManagedChannel channel;
    private final ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
    private final ChatServiceGrpc.ChatServiceStub asyncStub;

    public WebSocketHandler() {
        // Initialize gRPC channel and stubs
        this.channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        this.blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        this.asyncStub = ChatServiceGrpc.newStub(channel);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("New WebSocket connection established: {}", session.getId());

        // Start streaming messages from gRPC service
        asyncStub.streamMessages(ChatProto.Empty.newBuilder().build(), new io.grpc.stub.StreamObserver<ChatProto.ChatMessage>() {
            @Override
            public void onNext(ChatProto.ChatMessage message) {
                try {
                    session.sendMessage(new TextMessage(gson.toJson(message)));
                } catch (Exception e) {
                    log.error("Error sending message to WebSocket client", e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in gRPC stream", t);
            }

            @Override
            public void onCompleted() {
                log.info("gRPC stream completed");
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Received message from WebSocket client: {}", message.getPayload());
        
        // Parse the message and forward to gRPC service
        ChatProto.ChatMessage chatMessage = gson.fromJson(message.getPayload(), ChatProto.ChatMessage.class);
        ChatProto.ChatResponse response = blockingStub.sendMessage(chatMessage);
        
        // Send response back to WebSocket client
        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("WebSocket connection closed: {}", session.getId());
    }
} 