package com.example.compare.common;

import java.util.Locale;
import java.util.Objects;

/**
 * Centralized encoder introduced after migration.
 */
public class TenantEncoder {

    public String encode(String source) {
        if (source == null) {
            return "";
        }
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("LEGACY::")) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return "TENANT::" + trimmed.replace(" ", "-").toUpperCase(Locale.ROOT);
    }

    public String decodeTenant(String token) {
        if (token == null || token.isBlank()) {
            return "shard-default";
        }
        String normalized = token.trim();
        if (normalized.length() <= 8) {
            return "shard-default";
        }
        return normalized.substring(0, 8).toLowerCase(Locale.ROOT);
    }

    public boolean isSameTenant(String left, String right) {
        return Objects.equals(encode(left), encode(right));
    }
}
