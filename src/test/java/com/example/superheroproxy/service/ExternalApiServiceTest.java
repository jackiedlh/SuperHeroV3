package com.example.superheroproxy.service;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalApiServiceTest {

    private static final String TEST_API_URL = "http://test.api";
    private static final String TEST_API_TOKEN = "test-token";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CircuitBreakerFactory circuitBreakerFactory;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private ResponseGenerator responseGenerator;

    private ExternalApiService externalApiService;

    @BeforeEach
    void setUp() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        externalApiService = new ExternalApiService(restTemplate, circuitBreakerFactory);
        
        // Set the private fields using ReflectionTestUtils
        ReflectionTestUtils.setField(externalApiService, "baseUrl", TEST_API_URL);
        ReflectionTestUtils.setField(externalApiService, "apiToken", TEST_API_TOKEN);
    }

    @Test
    void getHero_Success() throws Exception {
        // Given
        String heroId = "1";
        String jsonResponse = "{\"id\":\"1\",\"name\":\"Superman\"}";
        Hero expectedHero = Hero.newBuilder()
                .setId("1")
                .setName("Superman")
                .build();

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // When
        Hero result = externalApiService.getHero(heroId);

        // Then
        assertNotNull(result);
        assertEquals(heroId, result.getId());
        verify(circuitBreakerFactory).create("getHero");
        verify(restTemplate).getForObject(contains(heroId), eq(String.class));
    }

    @Test
    void getHero_CircuitBreakerFallback() throws Exception {
        // Given
        String heroId = "1";
        Hero fallbackHero = Hero.newBuilder()
                .setId(heroId)
                .setName("Unknown Hero")
                .build();

        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Function<Throwable, ?> fallback = invocation.getArgument(1);
            return fallback.apply(new RuntimeException("Service unavailable"));
        });

        // When
        Hero result = externalApiService.getHero(heroId);

        // Then
        assertNotNull(result);
        assertEquals(heroId, result.getId());
        assertEquals("Unknown Hero", result.getName());
        verify(circuitBreakerFactory).create("getHero");
        verify(restTemplate, never()).getForObject(anyString(), any());
    }

    @Test
    void searchHero_Success() throws Exception {
        // Given
        String searchName = "super";
        String jsonResponse = "{\"results\":[{\"id\":\"1\",\"name\":\"Superman\"}]}";
        SearchResponse expectedResponse = SearchResponse.newBuilder()
                .setResponse("success")
                .setResultsFor(searchName)
                .addAllResults(List.of())
                .build();

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // When
        SearchResponse result = externalApiService.searchHero(searchName);

        // Then
        assertNotNull(result);
        verify(circuitBreakerFactory).create("searchHero");
        verify(restTemplate).getForObject(contains(searchName), eq(String.class));
    }

    @Test
    void searchHero_CircuitBreakerFallback() throws Exception {
        // Given
        String searchName = "super";
        SearchResponse fallbackResponse = SearchResponse.newBuilder()
                .setResponse("error")
                .setResultsFor(searchName)
                .addAllResults(List.of())
                .build();

        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Function<Throwable, ?> fallback = invocation.getArgument(1);
            return fallback.apply(new RuntimeException("Service unavailable"));
        });

        // When
        SearchResponse result = externalApiService.searchHero(searchName);

        // Then
        assertNotNull(result);
        assertEquals("error", result.getResponse());
        assertEquals(searchName, result.getResultsFor());
        assertTrue(result.getResultsList().isEmpty());
        verify(circuitBreakerFactory).create("searchHero");
        verify(restTemplate, never()).getForObject(anyString(), any());
    }

    @Test
    void getHero_JsonProcessingException() throws Exception {
        // Given
        String heroId = "1";
        String jsonResponse = "invalid json";

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // When & Then
        assertThrows(RuntimeException.class, () -> externalApiService.getHero(heroId));
        verify(circuitBreakerFactory).create("getHero");
        verify(restTemplate).getForObject(contains(heroId), eq(String.class));
    }
} 