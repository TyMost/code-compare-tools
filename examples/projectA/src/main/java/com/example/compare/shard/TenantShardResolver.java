package com.example.compare.shard;

import java.util.Locale;

/**
 * Initial shard resolver that pieces identifiers through substring calls.
 */
public class TenantShardResolver {

    public String resolve(String token) {
        if (token == null || token.length() < 8) {
            return "shard-default";
        }
        String prefix = token.substring(0, 8);
        if ("tenant01".equalsIgnoreCase(prefix)) {
            return "shard-a";
        }
        String suffix = token.substring(8);
        if (suffix.contains("vip")) {
            return "shard-b";
        }
        return "shard-default";
    }

    public String fallbackShard(String token) {
        if (token == null || token.isBlank()) {
            return "misc";
        }
        String ending = token.substring(Math.max(token.length() - 4, 0));
        return ending.toLowerCase(Locale.ROOT);
    }
}
