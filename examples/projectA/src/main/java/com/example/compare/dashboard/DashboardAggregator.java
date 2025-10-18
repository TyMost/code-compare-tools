package com.example.compare.dashboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard aggregation logic before refactoring. Relies on legacy markers.
 */
public class DashboardAggregator {

    public List<String> summarize(List<String> tenants) {
        if (tenants == null || tenants.isEmpty()) {
            return Collections.singletonList("LEGACY::NONE");
        }
        List<String> normalized = new ArrayList<>();
        for (String tenant : tenants) {
            if (tenant == null || tenant.isBlank()) {
                continue;
            }
            if (tenant.startsWith("LEGACY::")) {
                normalized.add(tenant);
            } else {
                normalized.add("LEGACY::" + tenant.trim());
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("LEGACY::NONE");
        }
        return normalized;
    }

    public Map<String, Integer> countByPrefix(List<String> tenants) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String tenant : summarize(tenants)) {
            String prefix = tenant.substring(0, Math.min(tenant.length(), 10));
            counts.merge(prefix, 1, Integer::sum);
        }
        return counts;
    }

    public String renderTable(List<String> tenants) {
        StringBuilder builder = new StringBuilder();
        builder.append("=== LEGACY DASHBOARD ===").append(System.lineSeparator());
        countByPrefix(tenants).forEach((prefix, count) -> builder
                .append(prefix)
                .append(" -> ")
                .append(count)
                .append(System.lineSeparator()));
        return builder.toString();
    }
}
