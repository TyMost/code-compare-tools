package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageDetail {

    /**
     * 参与评估的文件相对路径。
     */
    private String filePath;
    /** 文件覆盖率（matchedLines / totalLines）。 */
    private double coverage;
    /** ΔO 中与 ΔG 匹配的加权行数（Σ 相似度 × 行数）。 */
    private double matchedLines;
    /** ΔO 为该文件贡献的总行数。 */
    private int totalLines;
    /** 达到相似度阈值的 ΔO 变更块集合。 */
    @Builder.Default
    private List<DiffBlock> matchedBlocks = new ArrayList<>();
    /** 未达到阈值或在 ΔG 中无匹配项的 ΔO 变更块集合。 */
    @Builder.Default
    private List<DiffBlock> unmatchedBlocks = new ArrayList<>();
}
