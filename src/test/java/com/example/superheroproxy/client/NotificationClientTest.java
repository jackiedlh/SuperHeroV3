package com.example.superheroproxy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.service.SuperheroInnerService;

@SpringBootTest
@ActiveProfiles("test")
public class NotificationClientTest {

    @Autowired
    private NotificationClient notificationClient;

    @Autowired
    private SuperheroInnerService superheroInnerService;

    @Autowired
    private CacheManager cacheManager;

    private CountDownLatch latch;
    private List<HeroUpdate> receivedUpdates;

    @BeforeEach
    void setUp() {
        receivedUpdates = new ArrayList<>();
        // Clear the cache before each test
        cacheManager.getCache("superheroCache").clear();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        notificationClient.shutdown();
    }


} 