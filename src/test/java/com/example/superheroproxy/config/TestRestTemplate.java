package com.example.superheroproxy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


public class TestRestTemplate extends RestTemplate {
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