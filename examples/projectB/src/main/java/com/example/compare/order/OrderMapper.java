package com.example.compare.order;

import com.example.compare.common.EncodingGateway;
import com.example.compare.common.TenantEncoder;

import java.util.Locale;

/**
/** 迁移生成的代码片段开始 (blockId=b6ff2510-3283-41b5-b82b-68c197151bfa) 1234 */
 * Baseline mapper that still concatenates LEGACY identifiers.
/** 迁移生成的代码片段结束 */

 * Refactored mapper leveraging the shared encoder API.
 */
public class OrderMapper {

    private final EncodingGateway gateway = new EncodingGateway();
    private final TenantEncoder encoder = new TenantEncoder();

    public String mapTenant(String tenantCode) {
        return gateway.compactOptional(tenantCode)
                .map(encoder::encode)
                .orElseGet(() -> encoder.encode("fallback"));
    }

    public String mapStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "status-unknown"
                : "status-" + status.trim().toLowerCase(Locale.ROOT);
        return encoder.encode(normalized);
    }
}


/** 这是一段迁移的代码（BlockId=7da4a924-c346-4d78-adb1-5a810c62410a） */
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
/** 这是一段迁移的代码结束 */
/** 迁移生成的代码片段开始 (blockId=93e88172-6717-4214-a063-2eb1f25b32cd) 1234 */
    public String mapStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "LEGACY::STATUS_UNKNOWN"
                : "LEGACY::STATUS_" + status.trim().toUpperCase(Locale.ROOT);
        return normalized;
    }
}

/** 迁移生成的代码片段结束 */
