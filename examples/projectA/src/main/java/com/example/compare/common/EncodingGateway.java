package com.example.compare.common;

import java.util.Objects;
import java.util.Optional;

/**
 * Legacy implementation that still relies on the static LEGACY prefix.
 * Acts as the source code that will be refactored by the agent rules.
 */
public class EncodingGateway {

    public static final String LEGACY_PREFIX = "LEGACY::";

    private final TenantNormalizer normalizer = new TenantNormalizer();

    public String compact(String rawTenant) {
        if (rawTenant == null || rawTenant.isBlank()) {
            return "";
        }
        String normalized = normalizer.normalize(rawTenant);
        if (normalized.startsWith(LEGACY_PREFIX)) {
            return applyLegacyEncoding(normalized);
        }
        return normalized;
    }

    public Optional<String> compactOptional(String rawTenant) {
        return Optional.ofNullable(rawTenant)
                .map(normalizer::normalize)
                .map(value -> value.startsWith(LEGACY_PREFIX)
                        ? applyLegacyEncoding(value)
                        : value)
                .filter(value -> !value.isBlank());
    }

    private String applyLegacyEncoding(String normalized) {
        String trimmed = normalized.substring(LEGACY_PREFIX.length());
        String sanitized = trimmed.replace(" ", "-");
        return "LEGACY::" + sanitized.toUpperCase();
    }

    static class TenantNormalizer {

        String normalize(String value) {
            String trimmed = value.trim();
            if (Objects.equals(trimmed, "LEGACY::GLOBAL")) {
                return trimmed;
            }
            return trimmed.replace("_", "-");
        }
    }
}
