package com.example.migratediff.domain.migration;

/**
 * 迁移差异块的决策类型，领域层根据差异内容给出动作建议。
 */
public enum DecisionType {
    /**
     * 目标仓库缺失实现，建议插入源仓库代码。
     */
    INSERT,
    /**
     * 源、目标两侧都有实现但内容不同，建议生成更新模板。
     */
    UPDATE,
    /**
     * 源侧不存在实现，提示目标仓库删除或手动处理。
     */
    DELETE,
    /**
     * 差异可忽略（内容一致或信息不足），直接跳过。
     */
    SKIP
}
