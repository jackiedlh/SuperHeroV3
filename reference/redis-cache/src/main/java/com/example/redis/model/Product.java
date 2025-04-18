package com.example.redis.model;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
} 