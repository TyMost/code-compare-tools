package com.example.migratediff.domain.migration;

import com.example.migratediff.domain.diff.DiffBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个差异块的迁移建议结果，供应用层与前端逐块展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationBlockResult {

    /**
     * 差异块在当前列表中的顺序编号，便于阅读。
     */
    private int index;
    /**
     * 决策类型：插入、更新、删除提示或跳过。
     */
    private DecisionType decisionType;
    /**
     * 兼容历史逻辑的原始差异块引用（通常指向 Gauss 侧差异块）。
     */
    private DiffBlock block;
    /**
     * 为该差异块生成的模板文本，可能为空字符串。
     */
    private String template;
    /**
     * 针对该差异块的提示信息，说明产生建议的原因。
     */
    private String message;
    /**
     * 若生成过程中出现异常，记录错误信息以便排查。
     */
    private String errorMessage;
    /**
     * 目标侧（G2）中的起始/结束行号，供应用阶段定位。
     */
    private Integer startLine;
    private Integer endLine;
    /**
     * 插入场景的参考行号（在该行之前插入），为空时表示插入到文件末尾。
     */
    private Integer insertBeforeLine;
    /**
     * 目标侧原始片段缓存，用于行匹配与精确替换。
     */
    private String originalSnippet;
    /**
     * 预览时读取目标文件得到的片段，用于定位失败时的补偿匹配。
     */
    private String snapshotSnippet;
    /**
     * Oracle 侧差异块及其片段，对应模板中的 ${deltaO}.
     */
    private DiffBlock oracleBlock;
    private String oracleSnippet;
    /**
     * Gauss 侧差异块及其片段，对应模板中的 ${deltaG} 与定位信息。
     */
    private DiffBlock gaussBlock;
    private String gaussSnippet;
}
