package com.example.migration.sharding;

public class ShardResolver {
    public String resolveShard(String customerId) {
        return "oracle-shard-" + (Math.abs(customerId.hashCode()) % 4);
    }
}
