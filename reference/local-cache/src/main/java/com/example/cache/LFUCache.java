package com.example.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of a Least Frequently Used (LFU) cache.
 * Uses multiple doubly linked lists to maintain frequency order and a HashMap for O(1) access.
 * @param <K> The type of keys maintained by this cache
 * @param <V> The type of mapped values
 */
public class LFUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private final Map<Integer, DoublyLinkedList<K, V>> frequencyMap;
    private int minFrequency;

    /**
     * Creates a new LFU cache with the specified capacity.
     * @param capacity The maximum number of entries the cache can hold
     * @throws IllegalArgumentException if capacity is less than or equal to 0
     */
    public LFUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.frequencyMap = new HashMap<>();
        this.minFrequency = 0;
    }

    @Override
    public V get(K key) {
        Node<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        // Update frequency
        updateFrequency(node);
        return node.value;
    }

    @Override
    public V put(K key, V value) {
        if (capacity == 0) {
            return null;
        }

        Node<K, V> node = cache.get(key);
        if (node != null) {
            // Update existing node
            V oldValue = node.value;
            node.value = value;
            updateFrequency(node);
            return oldValue;
        }

        // Remove least frequently used if capacity exceeded
        if (cache.size() >= capacity) {
            DoublyLinkedList<K, V> minFreqList = frequencyMap.get(minFrequency);
            Node<K, V> last = minFreqList.removeLast();
            cache.remove(last.key);
        }

        // Create new node with frequency 1
        node = new Node<>(key, value);
        node.frequency = 1;
        cache.put(key, node);
        frequencyMap.computeIfAbsent(1, k -> new DoublyLinkedList<>()).addFirst(node);
        minFrequency = 1;
        return null;
    }

    @Override
    public V remove(K key) {
        Node<K, V> node = cache.remove(key);
        if (node != null) {
            DoublyLinkedList<K, V> list = frequencyMap.get(node.frequency);
            list.remove(node);
            if (list.isEmpty()) {
                frequencyMap.remove(node.frequency);
                if (minFrequency == node.frequency) {
                    minFrequency++;
                }
            }
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
        frequencyMap.clear();
        minFrequency = 0;
    }

    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    private void updateFrequency(Node<K, V> node) {
        // Remove from current frequency list
        DoublyLinkedList<K, V> oldList = frequencyMap.get(node.frequency);
        oldList.remove(node);
        
        // Update min frequency if needed
        if (oldList.isEmpty()) {
            frequencyMap.remove(node.frequency);
            if (minFrequency == node.frequency) {
                minFrequency++;
            }
        }

        // Add to new frequency list
        node.frequency++;
        frequencyMap.computeIfAbsent(node.frequency, k -> new DoublyLinkedList<>()).addFirst(node);
    }

    private static class Node<K, V> {
        K key;
        V value;
        int frequency;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
            this.frequency = 0;
        }
    }

    private static class DoublyLinkedList<K, V> {
        private final Node<K, V> head;
        private final Node<K, V> tail;

        DoublyLinkedList() {
            this.head = new Node<>(null, null);
            this.tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }

        void addFirst(Node<K, V> node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
        }

        void remove(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        Node<K, V> removeLast() {
            if (isEmpty()) {
                return null;
            }
            Node<K, V> last = tail.prev;
            remove(last);
            return last;
        }

        boolean isEmpty() {
            return head.next == tail;
        }
    }
} 