package com.example.demo.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DemoService {

    // Circuit Breaker Demo
    @CircuitBreaker(name = "backendA", fallbackMethod = "fallback")
    public String circuitBreakerDemo(boolean shouldFail) {
        if (shouldFail) {
            throw new RuntimeException("Simulated failure for circuit breaker");
        }
        return "Circuit breaker demo - success";
    }

    // Rate Limiter Demo
    @RateLimiter(name = "backendA")
    public String rateLimiterDemo() {
        return "Rate limiter demo - success";
    }

    // Retry Demo
    @Retry(name = "backendA", fallbackMethod = "retryFallback")
    public String retryDemo(boolean shouldFail) {
        if (shouldFail) {
            log.info("Retry attempt failed");
            throw new RuntimeException("Simulated failure for retry");
        }
        return "Retry demo - success";
    }

    // Bulkhead Demo
    @Bulkhead(name = "backendA")
    public String bulkheadDemo() throws InterruptedException {
        // Simulate some work
        Thread.sleep(1000);
        return "Bulkhead demo - success";
    }

    // Time Limiter Demo
    @TimeLimiter(name = "backendA")
    public CompletableFuture<String> timeLimiterDemo() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate a long-running task
                Thread.sleep(3000);
                return "Time limiter demo - success";
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // Fallback methods
    public String fallback(boolean shouldFail, Exception e) {
        return "Circuit breaker fallback - service is temporarily unavailable";
    }

    public String retryFallback(boolean shouldFail, Exception e) {
        return "Retry fallback - all retry attempts failed";
    }
} 