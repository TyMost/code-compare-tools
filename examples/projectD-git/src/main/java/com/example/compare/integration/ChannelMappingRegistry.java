package com.example.compare.integration;

/** 迁移生成的代码片段开始 (blockId=6bcbf76e-c734-40e3-a016-c98059316e0c) 1234 */
import java.util.HashMap;
/** 迁移生成的代码片段结束 */

import com.example.compare.common.TenantEncoder;

import java.util.Map;

/**
/** 迁移生成的代码片段开始 (blockId=6f97d363-3734-418d-89fb-b39ff821f024) 1234 */
 * Legacy channel mapping that builds a mutable map for downstream systems.
/** 迁移生成的代码片段结束 */

 * Registry now returns an immutable view computed through the encoder.
 */
public class ChannelMappingRegistry {

    private final TenantEncoder encoder = new TenantEncoder();

    public Map<String, String> registry() {
/** 迁移生成的代码片段开始 (blockId=ca864e44-1851-420f-bc0b-c3913ec47f30) 1234 */
        Map<String, String> mapping = new HashMap<>();
        mapping.put("web", "LEGACY::WEB");
        mapping.put("pos", "LEGACY::POS");
        mapping.put("partner", "LEGACY::PARTNER");
        mapping.put("mobile", "LEGACY::MOBILE");
        return mapping;
/** 迁移生成的代码片段结束 */

        Map<String, String> mapping = Map.of(
                "web", encoder.encode("web"),
                "pos", encoder.encode("pos"),
                "partner", encoder.encode("partner"),
                "mobile", encoder.encode("mobile")
        );
        return Map.copyOf(mapping);
    }
}
