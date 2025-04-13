package com.example.superheroproxy.config;

import org.springframework.web.client.RestTemplate;

public class TestRestTemplate extends RestTemplate {
    private String response;
    private int callCount = 0;

    public void setResponse(String response) {
        this.response = response;
    }

    public int getCallCount() {
        return callCount;
    }

    @Override
    public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
        callCount++;
        if (responseType == String.class) {
            @SuppressWarnings("unchecked")
            T result = (T) response;
            return result;
        }
        return null;
    }
}