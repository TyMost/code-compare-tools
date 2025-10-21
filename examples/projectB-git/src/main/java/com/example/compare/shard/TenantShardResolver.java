package com.example.compare.shard;

import com.example.compare.common.TenantEncoder;

import java.util.Locale;

/**
/** 迁移生成的代码片段开始 (blockId=4293f166-ce42-41d4-b339-fd0153ee945b) 1234 */
 * Initial shard resolver that pieces identifiers through substring calls.
/** 迁移生成的代码片段结束 */

 * Resolver rewritten to rely on decoder helper.
 */
public class TenantShardResolver {

    private final TenantEncoder encoder = new TenantEncoder();

    public String resolve(String token) {
/** 迁移生成的代码片段开始 (blockId=cb1064df-86a9-4103-b756-6b59ce443175) 1234 */
        if (token == null || token.length() < 8) {
            return "shard-default";
/** 迁移生成的代码片段结束 */

        String shardKey = encoder.decodeTenant(token);
        switch (shardKey) {
            case "tenant01":
                return "shard-a";
            case "tenant02":
                return "shard-b";
            default:
                return "shard-default";
        }
/** 迁移生成的代码片段开始 (blockId=afca77c8-f88f-43cc-8882-2bc355502c87) 1234 */
        String prefix = token.substring(0, 8);
        if ("tenant01".equalsIgnoreCase(prefix)) {
            return "shard-a";
        }
        String suffix = token.substring(8);
        if (suffix.contains("vip")) {
            return "shard-b";
        }
        return "shard-default";
/** 迁移生成的代码片段结束 */

    }

    public String fallbackShard(String token) {
/** 迁移生成的代码片段开始 (blockId=a226934d-3338-4ccb-b9ec-fb2ce11e6d14) 1234 */
        if (token == null || token.isBlank()) {
/** 迁移生成的代码片段结束 */

        String shardKey = encoder.decodeTenant(token);
        if (shardKey.isBlank()) {
            return "misc";
        }
/** 迁移生成的代码片段开始 (blockId=c0aa5dc5-3cb2-44ff-866c-dc02e91f747f) 1234 */
        String ending = token.substring(Math.max(token.length() - 4, 0));
        return ending.toLowerCase(Locale.ROOT);
/** 迁移生成的代码片段结束 */

        int start = Math.max(shardKey.length() - 4, 0);
        return shardKey.substring(start).toLowerCase(Locale.ROOT);
    }
}
