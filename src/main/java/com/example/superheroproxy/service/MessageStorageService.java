package com.example.superheroproxy.service;

import org.springframework.stereotype.Service;
import com.example.superheroproxy.proto.HeroUpdate;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class MessageStorageService {
    private final ConcurrentLinkedQueue<HeroUpdate> messages = new ConcurrentLinkedQueue<>();
    private static final int MAX_MESSAGES = 100;

    public void addMessage(HeroUpdate message) {
        messages.offer(message);
        // Keep only the last MAX_MESSAGES messages
        while (messages.size() > MAX_MESSAGES) {
            messages.poll();
        }
    }

    public List<HeroUpdate> getMessages() {
        return new ArrayList<>(messages);
    }

    public void clearMessages() {
        messages.clear();
    }
} 