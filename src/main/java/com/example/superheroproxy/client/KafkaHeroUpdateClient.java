package com.example.superheroproxy.client;

import com.example.superheroproxy.proto.HeroUpdate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaHeroUpdateClient {
//    private static final Logger logger = LoggerFactory.getLogger(KafkaHeroUpdateClient.class);
    private final KafkaConsumer<String, byte[]> consumer;
    private final Set<String> subscribedHeroIds;
    private final AtomicBoolean isActive;
    private final Scanner scanner;

    public KafkaHeroUpdateClient(String bootstrapServers, String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        this.consumer = new KafkaConsumer<>(props);
        this.subscribedHeroIds = ConcurrentHashMap.newKeySet();
        this.isActive = new AtomicBoolean(true);
        this.scanner = new Scanner(System.in);
    }

    public void subscribeToHeroes(String... heroIds) {
        if (heroIds.length == 0) {
            System.out.println("Subscribing to all heroes");
            consumer.subscribe(Arrays.asList("hero-updates"));
        } else {
            System.out.println("Subscribing to heroes: "+ Arrays.toString(heroIds));
            subscribedHeroIds.addAll(Arrays.asList(heroIds));
            consumer.subscribe(Arrays.asList("hero-updates"));
        }
    }

    private void printHeroUpdate(HeroUpdate update) {
        System.out.println("\n=== New Hero Update ===");
        System.out.println("Hero ID: " + update.getHeroId());
        System.out.println("Hero Name: " + update.getHero().getName());
        System.out.println("Update Type: " + update.getUpdateType());
        System.out.println("=====================\n");
    }

    private void handleCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help":
                printHelp();
                break;
            case "subscribe":
                if (parts.length > 1) {
                    String[] heroIds = Arrays.copyOfRange(parts, 1, parts.length);
                    subscribeToHeroes(heroIds);
                } else {
                    subscribeToHeroes();
                }
                break;
            case "status":
                System.out.println("Client Status:");
                System.out.println("Active: " + isActive.get());
                System.out.println("Subscribed Heroes: " + subscribedHeroIds);
                break;
            case "exit":
                isActive.set(false);
                break;
            default:
                System.out.println("Unknown command. Type 'help' for available commands.");
        }
    }

    private void printHelp() {
        System.out.println("\nAvailable Commands:");
        System.out.println("help - Show this help message");
        System.out.println("subscribe [heroId1 heroId2 ...] - Subscribe to updates for specific heroes or all heroes");
        System.out.println("status - Show current client status");
        System.out.println("exit - Exit the client");
        System.out.println();
    }

    public void start() {
        // Start a separate thread for command handling
        Thread commandThread = new Thread(() -> {
            while (isActive.get()) {
                System.out.print("> ");
                String command = scanner.nextLine();
                handleCommand(command);
            }
        });
        commandThread.start();

        // Main message processing loop
        try {
            while (isActive.get()) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, byte[]> record : records) {
                    try {
                        HeroUpdate update = HeroUpdate.parseFrom(record.value());
                        
                        // If subscribed to specific heroes, filter messages
                        if (!subscribedHeroIds.isEmpty() && !subscribedHeroIds.contains(update.getHeroId())) {
                            continue;
                        }
                        
                        printHeroUpdate(update);
                    } catch (Exception e) {
                        System.err.println("Error processing message: "+e.getMessage());
                    }
                }
            }
        } finally {
            consumer.close();
            scanner.close();
        }
    }

    public static void main(String[] args) {
        // Create client with a different consumer group
        KafkaHeroUpdateClient client = new KafkaHeroUpdateClient(
            "localhost:9092",  // Kafka bootstrap servers
            "hero-update-direct-consumer-group"  // Different consumer group ID
        );

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down client...");
            client.isActive.set(false);
        }));

        try {
            // Print welcome message and help
            System.out.println("\nWelcome to SuperHero Kafka Client!");
            System.out.println("Would you like to subscribe to specific heroes? (y/n)");
            Scanner initialScanner = new Scanner(System.in);
            String response = initialScanner.nextLine().trim().toLowerCase();
            
            if (response.equals("y")) {
                System.out.println("Enter hero IDs separated by spaces (or press Enter for none):");
                String heroInput = initialScanner.nextLine().trim();
                if (heroInput.isEmpty()) {
                    System.out.println("Subscribing to updates for all heroes...");
                    client.subscribeToHeroes();
                } else {
                    String[] heroIds = heroInput.split("\\s+");
                    System.out.println("Subscribing to updates for heroes: " + Arrays.toString(heroIds));
                    client.subscribeToHeroes(heroIds);
                }
            } else {
                System.out.println("Subscribing to updates for all heroes...");
                client.subscribeToHeroes();
            }

            System.out.println("Type 'help' for available commands");
            System.out.println("Press Ctrl+C to exit\n");

            // Start the client
            client.start();

        } catch (Exception e) {
            System.err.println("Error in client: " + e.getMessage());
        }
    }
} 