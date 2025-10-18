package com.example.compare.order;

import com.example.compare.common.EncodingGateway;

import java.util.Locale;

/**
 * Baseline mapper that still concatenates LEGACY identifiers.
 */
public class OrderMapper {

    private final EncodingGateway gateway = new EncodingGateway();

    public String mapTenant(String tenantCode) {
        if (tenantCode == null) {
            return "LEGACY::UNKNOWN";
        }
        String compacted = gateway.compact(tenantCode);
        if (compacted.isBlank()) {
            return "LEGACY::UNSET";
        }
        return "LEGACY::" + compacted;
    }

    public String mapStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "LEGACY::STATUS_UNKNOWN"
                : "LEGACY::STATUS_" + status.trim().toUpperCase(Locale.ROOT);
        return normalized;
    }
}
