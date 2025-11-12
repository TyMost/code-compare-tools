package com.example.migration.sharding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GaussShardRoutingService {
    private final GaussShardStrategy strategy = new GaussShardStrategy();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String resolve(String customerId) {
        return cache.computeIfAbsent(customerId, strategy::route);
    }
}
