package com.example.codecompare.rebuild.repository.model;

/**
 * 文件变更类型枚举，对齐 Git 与差异分析的语义。
 */
public enum FileChangeType {
    NEW,
    MODIFIED,
    DELETED,
    RENAMED,
    UNCHANGED
}
