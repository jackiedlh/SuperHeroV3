package com.example.redis.service;

import com.example.redis.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ProductService {
    // Simulated database
    private final List<Product> products = new ArrayList<>();

    public ProductService() {
        // Initialize with some sample data
        products.add(new Product(1L, "Laptop", "High-performance laptop", new BigDecimal("999.99"), 10));
        products.add(new Product(2L, "Smartphone", "Latest smartphone", new BigDecimal("699.99"), 20));
        products.add(new Product(3L, "Tablet", "Portable tablet", new BigDecimal("399.99"), 15));
    }

    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) {
        log.info("Fetching product from database: {}", id);
        simulateSlowService();
        return products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Cacheable(value = "products")
    public List<Product> getAllProducts() {
        log.info("Fetching all products from database");
        simulateSlowService();
        return new ArrayList<>(products);
    }

    @CachePut(value = "products", key = "#product.id")
    public Product createProduct(Product product) {
        log.info("Creating new product: {}", product);
        products.add(product);
        return product;
    }

    @CachePut(value = "products", key = "#id")
    public Product updateProduct(Long id, Product product) {
        log.info("Updating product: {}", id);
        Optional<Product> existingProduct = products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();

        if (existingProduct.isPresent()) {
            Product toUpdate = existingProduct.get();
            toUpdate.setName(product.getName());
            toUpdate.setDescription(product.getDescription());
            toUpdate.setPrice(product.getPrice());
            toUpdate.setStock(product.getStock());
            return toUpdate;
        }
        return null;
    }

    @CacheEvict(value = "products", key = "#id" )
    public void deleteProduct(Long id) {
        log.info("Deleting product: {}", id);
        products.removeIf(p -> p.getId().equals(id));
    }

    @CacheEvict(value = "products", allEntries = true)
    public void clearCache() {
        log.info("Clearing all product caches");
    }

    private void simulateSlowService() {
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 