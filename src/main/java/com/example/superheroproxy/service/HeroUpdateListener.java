package com.example.superheroproxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.HeroUpdate;
import com.example.superheroproxy.proto.UpdateType;

@Service
public class HeroUpdateListener {
    private static final Logger logger = LoggerFactory.getLogger(HeroUpdateListener.class);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MessageStorageService messageStorageService;

    @KafkaListener(
        topics = "${kafka.topic.hero-updates}",
        groupId = "hero-update-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void listenHeroUpdates(HeroUpdate update) {
        try {
            String heroId = update.getHeroId();
            Hero hero = update.getHero();
            UpdateType updateType = update.getUpdateType();

            logger.info("Received hero update for hero: {}, type: {}", heroId, updateType);
            
            // Store the message
            messageStorageService.addMessage(update);
            
            // Process the update
            switch (updateType) {
                case NEW:
                    logger.info("New hero added: {}", heroId);
                    break;
                case UPDATED:
                    logger.info("Hero updated: {}", heroId);
                    break;
                case DELETED:
                    logger.info("Hero deleted: {}", heroId);
                    break;
                default:
                    logger.warn("Unknown update type: {} for hero: {}", updateType, heroId);
            }

            // You can add additional processing logic here
            // For example, updating a database, triggering other services, etc.

        } catch (Exception e) {
            logger.error("Error processing hero update: {}", e.getMessage(), e);
        }
    }
} 