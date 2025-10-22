package com.example.compare.integration;

/**
/** 迁移生成的代码片段开始 (blockId=b419cac4-c8a7-46f1-873a-7af2c9f1bab6) 1234 */
 * Helper that remains mostly unchanged between migrations.
/** 迁移生成的代码片段结束 */

 * Bridge remains mostly identical after migration with minor cleanup.
 */
public class PlatformBridge {

    public String bridge(String payload) {
/** 迁移生成的代码片段开始 (blockId=d32f4e5d-4b48-4f67-992b-5b2066a44a00) 1234 */
        return payload == null ? "offline" : payload.trim();
/** 迁移生成的代码片段结束 */

        return payload == null ? "offline" : payload.strip();
    }

    public boolean isHealthy() {
        return true;
    }
}
