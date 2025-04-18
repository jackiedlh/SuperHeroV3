package com.example.cache;

public class CacheTest {
    public static void main(String[] args) {
        // Test LRU Cache
        System.out.println("Testing LRU Cache:");
        Cache<Integer, String> lruCache = new LRUCache<>(3);
        
        lruCache.put(1, "One");
        lruCache.put(2, "Two");
        lruCache.put(3, "Three");
        System.out.println("After adding 3 elements: " + lruCache.get(1)); // Should print "One"
        
        lruCache.put(4, "Four"); // This should evict "Two" as it's the least recently used
        System.out.println("After adding 4th element: " + lruCache.get(2)); // Should print null
        
        // Access "One" to make it most recently used
        lruCache.get(1);
        lruCache.put(5, "Five"); // This should evict "Three" as it's the least recently used
        System.out.println("After adding 5th element: " + lruCache.get(3)); // Should print null
        System.out.println("After adding 5th element: " + lruCache.get(1)); // Should print "One"
        
        // Test LFU Cache
        System.out.println("\nTesting LFU Cache:");
        Cache<Integer, String> lfuCache = new LFUCache<>(3);
        
        lfuCache.put(1, "One");
        lfuCache.put(2, "Two");
        lfuCache.put(3, "Three");
        
        // Access some elements multiple times
        lfuCache.get(1);
        lfuCache.get(1);
        lfuCache.get(2);
        
        // Add a new element - should evict "Three" as it's least frequently used
        lfuCache.put(4, "Four");
        System.out.println("After adding 4th element: " + lfuCache.get(3)); // Should print null
        
        // Access "Two" again
        lfuCache.get(2);
        
        // Add another element - should evict "Four" as it's least frequently used
        lfuCache.put(5, "Five");
        System.out.println("After adding 5th element: " + lfuCache.get(4)); // Should print null
        System.out.println("After adding 5th element: " + lfuCache.get(1)); // Should print "One"
    }
} 