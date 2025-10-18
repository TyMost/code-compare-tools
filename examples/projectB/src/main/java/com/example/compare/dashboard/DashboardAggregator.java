package com.example.compare.dashboard;

/** 迁移生成的代码片段开始 (blockId=108f5555-25a1-424d-9b3c-37fa0844d649) 1234 */
import java.util.ArrayList;
/** 迁移生成的代码片段结束 */

import com.example.compare.common.EncodingGateway;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
/** 迁移生成的代码片段开始 (blockId=7b3398a3-6d5e-46d6-aa25-b47755b3743e) 1234 */
 * Dashboard aggregation logic before refactoring. Relies on legacy markers.
/** 迁移生成的代码片段结束 */

 * Dashboard aggregation after migration with encoder-driven normalization.
 */
public class DashboardAggregator {

    private final EncodingGateway gateway = new EncodingGateway();

    public List<String> summarize(List<String> tenants) {
        if (tenants == null || tenants.isEmpty()) {
/** 迁移生成的代码片段开始 (blockId=c79668ce-ed00-4b75-adb7-3a6101bb58f4) 1234 */
            return Collections.singletonList("LEGACY::NONE");
/** 迁移生成的代码片段结束 */

            return Collections.singletonList(gateway.compact("tenant::none"));
        }
/** 迁移生成的代码片段开始 (blockId=d93591fa-ca86-4ffc-8319-fb918e3f2fef) 1234 */
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
/** 迁移生成的代码片段结束 */

        return tenants.stream()
                .map(gateway::compact)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
    }

/** 迁移生成的代码片段开始 (blockId=d139be42-300a-4f81-95d3-75ba86112dd8) 1234 */
    public Map<String, Integer> countByPrefix(List<String> tenants) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String tenant : summarize(tenants)) {
            String prefix = tenant.substring(0, Math.min(tenant.length(), 10));
            counts.merge(prefix, 1, Integer::sum);
        }
        return counts;
/** 迁移生成的代码片段结束 */

    public Map<String, Long> countByPrefix(List<String> tenants) {
        return summarize(tenants).stream()
                .collect(Collectors.groupingBy(
                        value -> value.substring(0, Math.min(10, value.length())),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    public String renderTable(List<String> tenants) {
/** 迁移生成的代码片段开始 (blockId=50367328-ea1c-4165-8e8e-1ad549047052) 1234 */
        StringBuilder builder = new StringBuilder();
        builder.append("=== LEGACY DASHBOARD ===").append(System.lineSeparator());
/** 迁移生成的代码片段结束 */

        StringBuilder builder = new StringBuilder("=== MODERN DASHBOARD ===")
                .append(System.lineSeparator());
        countByPrefix(tenants).forEach((prefix, count) -> builder
                .append(prefix)
                .append(" -> ")
                .append(count)
                .append(System.lineSeparator()));
        return builder.toString();
    }
}
