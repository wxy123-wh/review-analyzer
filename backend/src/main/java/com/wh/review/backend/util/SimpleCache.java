package com.wh.review.backend.util;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SimpleCache<K, V> {

    private final long ttlMillis;
    private final Map<K, CacheEntry<V>> values = new ConcurrentHashMap<>();

    public SimpleCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public V get(K key, Supplier<V> loader) {
        CacheEntry<V> entry = values.get(key);
        long now = Instant.now().toEpochMilli();
        if (entry != null && now - entry.createdAtMillis() <= ttlMillis) {
            return entry.value();
        }
        V value = loader.get();
        values.put(key, new CacheEntry<>(value, now));
        return value;
    }

    private record CacheEntry<V>(V value, long createdAtMillis) {
    }
}
