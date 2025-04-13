package com.example.superheroproxy.dto;

public class HeroDto {
    private String id;
    private String name;

    public HeroDto(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
} 