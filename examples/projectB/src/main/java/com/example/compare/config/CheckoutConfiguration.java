package com.example.compare.config;

import java.util.Map;

public class CheckoutConfiguration {

/** 迁移生成的代码片段开始 (blockId=0121e3a9-a038-4a76-b7db-64ea19f3941c) 1234 */
    public void apply(Map<String, Object> overrides) {
        setNewCheckout(true);
/** 迁移生成的代码片段结束 */

    public void apply(Map<String, Object> values) {
        override("checkout", values);
    }

/** 迁移生成的代码片段开始 (blockId=12de89d5-323c-476d-abd9-14f5924722c4) 1234 */
    void setNewCheckout(boolean value) {
        // legacy toggling stub
/** 迁移生成的代码片段结束 */

    void override(String key, Map<String, Object> values) {
        Map.copyOf(values);
    }
}
