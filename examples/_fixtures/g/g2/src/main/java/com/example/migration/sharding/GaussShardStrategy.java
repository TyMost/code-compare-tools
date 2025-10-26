package com.example.migration.sharding;

public class GaussShardStrategy {
    public String route(String customerId) {
        int bucket = Math.abs(customerId.hashCode()) % 8;
        return "gauss-shard-" + bucket;
    }
}
