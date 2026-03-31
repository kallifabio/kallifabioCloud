package de.kallifabio.cloud.pluginapi.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleTtlCache<K, V> {

    private final Map<K, TimedValue<V>> store = new ConcurrentHashMap<>();

    public void put(K key, V value, long ttlMs) {
        long expiresAt = System.currentTimeMillis() + Math.max(1L, ttlMs);
        store.put(key, new TimedValue<>(value, expiresAt));
    }

    public Optional<V> get(K key) {
        TimedValue<V> timed = store.get(key);
        if (timed == null) {
            return Optional.empty();
        }
        if (timed.isExpired(System.currentTimeMillis())) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.ofNullable(timed.value());
    }

    public void invalidate(K key) {
        store.remove(key);
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
