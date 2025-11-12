package com.example.migration.sharding;

public class ShardResolver {
    private final GaussShardStrategy strategy = new GaussShardStrategy();

    public String resolveShard(String customerId) {
        return strategy.route(customerId);
    }
}
