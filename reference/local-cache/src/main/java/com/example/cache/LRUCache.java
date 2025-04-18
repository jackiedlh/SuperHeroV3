package com.example.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of a Least Recently Used (LRU) cache.
 * Uses a doubly linked list to maintain access order and a HashMap for O(1) access.
 * @param <K> The type of keys maintained by this cache
 * @param <V> The type of mapped values
 */
public class LRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private final Node<K, V> head;
    private final Node<K, V> tail;

    /**
     * Creates a new LRU cache with the specified capacity.
     * @param capacity The maximum number of entries the cache can hold
     * @throws IllegalArgumentException if capacity is less than or equal to 0
     */
    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public V get(K key) {
        Node<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        // Move the accessed node to the front
        moveToFront(node);
        return node.value;
    }

    @Override
    public V put(K key, V value) {
        Node<K, V> node = cache.get(key);
        if (node != null) {
            // Update existing node
            V oldValue = node.value;
            node.value = value;
            moveToFront(node);
            return oldValue;
        }

        // Create new node
        node = new Node<>(key, value);
        cache.put(key, node);
        addToFront(node);

        // Remove least recently used if capacity exceeded
        if (cache.size() > capacity) {
            Node<K, V> last = removeLast();
            cache.remove(last.key);
            return null;
        }
        return null;
    }

    @Override
    public V remove(K key) {
        Node<K, V> node = cache.remove(key);
        if (node != null) {
            removeNode(node);
            return node.value;
        }
        return null;
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public void clear() {
        cache.clear();
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    private void moveToFront(Node<K, V> node) {
        removeNode(node);
        addToFront(node);
    }

    private void addToFront(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private Node<K, V> removeLast() {
        Node<K, V> last = tail.prev;
        removeNode(last);
        return last;
    }

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
} 