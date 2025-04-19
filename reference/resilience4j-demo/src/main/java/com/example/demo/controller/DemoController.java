package com.example.demo.controller;

import com.example.demo.service.DemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    @GetMapping("/circuit-breaker")
    public ResponseEntity<String> circuitBreakerDemo(@RequestParam(defaultValue = "false") boolean shouldFail) {
        return ResponseEntity.ok(demoService.circuitBreakerDemo(shouldFail));
    }

    @GetMapping("/rate-limiter")
    public ResponseEntity<String> rateLimiterDemo() {
        return ResponseEntity.ok(demoService.rateLimiterDemo());
    }

    @GetMapping("/retry")
    public ResponseEntity<String> retryDemo(@RequestParam(defaultValue = "false") boolean shouldFail) {
        return ResponseEntity.ok(demoService.retryDemo(shouldFail));
    }

    @GetMapping("/bulkhead")
    public ResponseEntity<String> bulkheadDemo() throws InterruptedException {
        return ResponseEntity.ok(demoService.bulkheadDemo());
    }

    @GetMapping("/time-limiter")
    public CompletableFuture<ResponseEntity<String>> timeLimiterDemo() {
        return demoService.timeLimiterDemo()
                .thenApply(ResponseEntity::ok);
    }
} 