package com.company.smartsupport.graph.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class GraphCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public Optional<Object> getPath(String projectId, String key) {
        return get("path", projectId, key);
    }

    public Optional<Object> getNeighbor(String projectId, String key) {
        return get("neighbor", projectId, key);
    }

    public Optional<Object> getEvidence(String projectId, String key) {
        return get("evidence", projectId, key);
    }

    public void putPath(String projectId, String key, Object value) {
        put("path", projectId, key, value);
    }

    public void putNeighbor(String projectId, String key, Object value) {
        put("neighbor", projectId, key, value);
    }

    public void putEvidence(String projectId, String key, Object value) {
        put("evidence", projectId, key, value);
    }

    public void invalidateProject(String projectId) {
        String prefix = projectId + ":";
        entries.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void invalidateOnGraphMutation(String projectId, String action) {
        if ("publish".equals(action) || "rollback".equals(action) || "unfreeze".equals(action)) {
            invalidateProject(projectId);
        }
    }

    private Optional<Object> get(String namespace, String projectId, String key) {
        CacheEntry entry = entries.get(cacheKey(namespace, projectId, key));
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    private void put(String namespace, String projectId, String key, Object value) {
        entries.put(cacheKey(namespace, projectId, key), new CacheEntry(value, Instant.now().plus(TTL)));
    }

    private String cacheKey(String namespace, String projectId, String key) {
        return projectId + ":" + namespace + ":" + key;
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}