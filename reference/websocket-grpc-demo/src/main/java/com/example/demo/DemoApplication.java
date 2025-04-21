package com.example.demo;

import com.example.demo.grpc.ChatServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) throws IOException, InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(DemoApplication.class, args);

        // Start gRPC server
        Server server = ServerBuilder.forPort(9090)
                .addService(context.getBean(ChatServiceImpl.class))
                .build();

        server.start();
        System.out.println("gRPC server started on port 9090");
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down gRPC server");
            server.shutdown();
        }));

        server.awaitTermination();
    }
} 