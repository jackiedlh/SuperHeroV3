package com.example.superheroproxy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Object handleAllExceptions(Exception ex, WebRequest request) {
        logger.error("Unhandled exception occurred", ex);
        
        // Check if this is an SSE request
        if (isSseRequest(request)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Internal Server Error\",\"message\":\"An unexpected error occurred\"}"));
                emitter.complete();
            } catch (IOException e) {
                logger.error("Error sending SSE error event", e);
            }
            return emitter;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        response.put("message", "An unexpected error occurred");
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        logger.warn("Invalid request parameter", ex);
        
        if (isSseRequest(request)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Bad Request\",\"message\":\"" + ex.getMessage() + "\"}"));
                emitter.complete();
            } catch (IOException e) {
                logger.error("Error sending SSE error event", e);
            }
            return emitter;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public Object handleGrpcException(StatusRuntimeException ex, WebRequest request) {
        logger.error("gRPC communication error", ex);
        
        if (isSseRequest(request)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Service Unavailable\",\"message\":\"Error communicating with the superhero service\"}"));
                emitter.complete();
            } catch (IOException e) {
                logger.error("Error sending SSE error event", e);
            }
            return emitter;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", "Error communicating with the superhero service");
        
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(RestClientException.class)
    public Object handleExternalApiException(RestClientException ex, WebRequest request) {
        logger.error("Error calling external API", ex);
        
        if (isSseRequest(request)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Service Unavailable\",\"message\":\"Error communicating with the external superhero API\"}"));
                emitter.complete();
            } catch (IOException e) {
                logger.error("Error sending SSE error event", e);
            }
            return emitter;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", "Error communicating with the external superhero API");
        
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(JsonProcessingException.class)
    public Object handleJsonProcessingException(JsonProcessingException ex, WebRequest request) {
        logger.error("Error processing JSON response", ex);
        
        if (isSseRequest(request)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Internal Server Error\",\"message\":\"Error processing API response\"}"));
                emitter.complete();
            } catch (IOException e) {
                logger.error("Error sending SSE error event", e);
            }
            return emitter;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        response.put("message", "Error processing API response");
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Cache.ValueRetrievalException.class)
    public Object handleCacheRetrievalException(Cache.ValueRetrievalException ex, WebRequest request) {
        logger.error("Error retrieving value from cache", ex);
        
        if (isSseRequest(request)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\":\"Internal Server Error\",\"message\":\"Error retrieving data from cache\"}"));
                emitter.complete();
            } catch (IOException e) {
                logger.error("Error sending SSE error event", e);
            }
            return emitter;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        response.put("message", "Error retrieving data from cache");
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean isSseRequest(WebRequest request) {
        String acceptHeader = request.getHeader("Accept");
        return acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
} 