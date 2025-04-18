package com.example.cache;

/**
 * Interface defining the common operations for a cache.
 * @param <K> The type of keys maintained by this cache
 * @param <V> The type of mapped values
 */
public interface Cache<K, V> {
    /**
     * Associates the specified value with the specified key in this cache.
     * @param key The key with which the specified value is to be associated
     * @param value The value to be associated with the specified key
     * @return The previous value associated with key, or null if there was no mapping for key
     */
    V put(K key, V value);

    /**
     * Returns the value to which the specified key is mapped, or null if this cache contains no mapping for the key.
     * @param key The key whose associated value is to be returned
     * @return The value to which the specified key is mapped, or null if this cache contains no mapping for the key
     */
    V get(K key);

    /**
     * Removes the mapping for a key from this cache if it is present.
     * @param key The key whose mapping is to be removed from the cache
     * @return The previous value associated with key, or null if there was no mapping for key
     */
    V remove(K key);

    /**
     * Returns the number of key-value mappings in this cache.
     * @return The number of key-value mappings in this cache
     */
    int size();

    /**
     * Removes all of the mappings from this cache.
     */
    void clear();

    /**
     * Returns true if this cache contains a mapping for the specified key.
     * @param key The key whose presence in this cache is to be tested
     * @return true if this cache contains a mapping for the specified key
     */
    boolean containsKey(K key);
} 