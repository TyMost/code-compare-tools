package com.example.migratediff.domain.coverage.optimization;

/**
 * 匹配类型枚举
 */
public enum MatchType {
    /**
     * 精确匹配 - 内容完全相同
     */
    EXACT,
    
    /**
     * 模糊匹配 - 编辑距离较小，内容高度相似
     */
    FUZZY,
    
    /**
     * 上下文扩展匹配 - 通过扩展上下文找到的匹配
     */
    CONTEXT,
    
    /**
     * 无匹配 - 未找到有效匹配
     */
    NONE
}
