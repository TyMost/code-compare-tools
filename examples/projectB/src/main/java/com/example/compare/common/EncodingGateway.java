package com.example.compare.common;

/** 迁移生成的代码片段开始 (blockId=8937656a-ca6c-4a7d-876c-69cf34e3b716) */
import java.util.Objects;
/** 迁移生成的代码片段结束 */
/** 迁移生成的代码片段开始 (blockId=204d2ec7-3b72-427e-a155-b771901512cc) */
public class EncodingGateway {
/** 迁移生成的代码片段结束 */

import java.util.Optional;

/** 迁移生成的代码片段开始 (blockId=c538cb03-be27-4457-a874-85df89b6bfbf) 1234 */
/**
 * Legacy implementation that still relies on the static LEGACY prefix.
 * Acts as the source code that will be refactored by the agent rules.
/** 迁移生成的代码片段结束 */

efactored gateway that routes through the shared encoder.
 */
public class EncodingGateway {

/** 迁移生成的代码片段开始 (blockId=f0ec1605-2c34-4c02-add6-b82d741e3752) 1234 */
    public static final String LEGACY_PREFIX = "LEGACY::";

/** 迁移生成的代码片段结束 */

    private final TenantEncoder encoder = new TenantEncoder();
    private final TenantNormalizer normalizer = new TenantNormalizer();

    public String compact(String rawTenant) {
        if (rawTenant == null || rawTenant.isBlank()) {
            return "";
        }
        String normalized = normalizer.normalize(rawTenant);
/** 迁移生成的代码片段开始 (blockId=e3292263-5060-46f4-9318-f707164e4e03) 1234 */
        if (normalized.startsWith(LEGACY_PREFIX)) {
            return applyLegacyEncoding(normalized);
        }
        return normalized;
/** 迁移生成的代码片段结束 */

        return encoder.encode(normalized);
    }

    public Optional<String> compactOptional(String rawTenant) {
        return Optional.ofNullable(rawTenant)
                .map(normalizer::normalize)
/** 迁移生成的代码片段开始 (blockId=f1edb4de-9b8e-40b5-98d8-13166969b7ad) 1234 */
                .map(value -> value.startsWith(LEGACY_PREFIX)
                        ? applyLegacyEncoding(value)
                        : value)
/** 迁移生成的代码片段结束 */

                .map(encoder::encode)
                .filter(value -> !value.isBlank());
    }

/** 迁移生成的代码片段开始 (blockId=206d0b26-5746-422a-bff4-d7973c7ff404) 1234 */
    private String applyLegacyEncoding(String normalized) {
        String trimmed = normalized.substring(LEGACY_PREFIX.length());
        String sanitized = trimmed.replace(" ", "-");
        return "LEGACY::" + sanitized.toUpperCase();
/** 迁移生成的代码片段结束 */

    public boolean isEncoded(String value) {
        return !encoder.encode(value).isBlank();
    }

    static class TenantNormalizer {

        String normalize(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
/** 迁移生成的代码片段开始 (blockId=49cdd42d-b1b7-4851-9f46-3eccf70589d7) 1234 */
            if (Objects.equals(trimmed, "LEGACY::GLOBAL")) {
                return trimmed;
/** 迁移生成的代码片段结束 */

            if (trimmed.startsWith("tenant://")) {
                return trimmed.substring("tenant://".length());
            }
            return trimmed.replace("_", "-");
        }
    }
}
