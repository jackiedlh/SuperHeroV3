package com.example.superheroproxy.service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Biography;
import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.PowerStats;
import com.example.superheroproxy.proto.SearchRequest;
import com.example.superheroproxy.proto.SearchResponse;

import io.grpc.stub.StreamObserver;

public class SuperheroServiceImplTest {

    private static class TestRestTemplate extends RestTemplate {
        private String response;

        public void setResponse(String response) {
            this.response = response;
        }

        @Override
        public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
            if (responseType == String.class) {
                @SuppressWarnings("unchecked")
                T result = (T) response;
                return result;
            }
            return null;
        }
    }

    private static class TestStreamObserver implements StreamObserver<SearchResponse> {
        private SearchResponse response;
        private Throwable error;
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onNext(SearchResponse value) {
            this.response = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }

        public SearchResponse getResponse() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
            return response;
        }

        public Throwable getError() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
            return error;
        }
    }

    private TestRestTemplate restTemplate;
    private SuperheroServiceImpl superheroService;

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        superheroService = new SuperheroServiceImpl(restTemplate);
        superheroService.setApiToken("test-token");
    }

    @Test
    void testSearchHero_Success() throws Exception {
        // Prepare test data
        String mockResponse = """
            {
                "response": "success",
                "results-for": "Batman",
                "results": [{
                    "id": "69",
                    "name": "Batman",
                    "powerstats": {
                        "intelligence": "81",
                        "strength": "40",
                        "speed": "29"
                    },
                    "biography": {
                        "full-name": "Terry McGinnis",
                        "publisher": "DC Comics"
                    },
                    "image": {
                        "url": "https://example.com/batman.jpg"
                    }
                }]
            }
            """;

        restTemplate.setResponse(mockResponse);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
            .setName("Batman")
            .build();

        // Execute test
        TestStreamObserver responseObserver = new TestStreamObserver();
        superheroService.searchHero(request, responseObserver);

        // Get and verify response
        SearchResponse response = responseObserver.getResponse();
        assertNotNull(response);
        assertEquals("success", response.getResponse());
        assertEquals("Batman", response.getResultsFor());
        assertEquals(1, response.getResultsCount());

        Hero hero = response.getResults(0);
        assertEquals("69", hero.getId());
        assertEquals("Batman", hero.getName());

        PowerStats powerStats = hero.getPowerstats();
        assertEquals("81", powerStats.getIntelligence());
        assertEquals("40", powerStats.getStrength());
        assertEquals("29", powerStats.getSpeed());

        Biography bio = hero.getBiography();
        assertEquals("Terry McGinnis", bio.getFullName());
        assertEquals("DC Comics", bio.getPublisher());

        assertEquals("https://example.com/batman.jpg", hero.getImage().getUrl());
    }

    @Test
    void testSearchHero_NoResults() throws Exception {
        // Prepare test data for no results
        String mockResponse = """
            {
                "response": "success",
                "results-for": "UnknownHero",
                "results": []
            }
            """;

        restTemplate.setResponse(mockResponse);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
            .setName("UnknownHero")
            .build();

        // Execute test
        TestStreamObserver responseObserver = new TestStreamObserver();
        superheroService.searchHero(request, responseObserver);

        // Get and verify response
        SearchResponse response = responseObserver.getResponse();
        assertNotNull(response);
        assertEquals("success", response.getResponse());
        assertEquals("UnknownHero", response.getResultsFor());
        assertEquals(0, response.getResultsCount());
    }

    @Test
    void testSearchHero_ApiError() throws Exception {
        // Set up error response
        restTemplate.setResponse(null);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
            .setName("Batman")
            .build();

        // Execute test
        TestStreamObserver responseObserver = new TestStreamObserver();
        superheroService.searchHero(request, responseObserver);

        // Verify error
        Throwable error = responseObserver.getError();
        assertNotNull(error);
    }

    @Test
    void testSearchHero_InvalidJson() throws Exception {
        // Prepare invalid JSON response
        String invalidJson = "invalid json";
        restTemplate.setResponse(invalidJson);

        // Create request
        SearchRequest request = SearchRequest.newBuilder()
            .setName("Batman")
            .build();

        // Execute test
        TestStreamObserver responseObserver = new TestStreamObserver();
        superheroService.searchHero(request, responseObserver);

        // Verify error
        Throwable error = responseObserver.getError();
        assertNotNull(error);
    }
} 