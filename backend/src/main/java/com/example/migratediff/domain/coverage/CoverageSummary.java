package com.example.migratediff.domain.coverage;

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
public class CoverageSummary {

    /**
     * 应用层下发的任务标识，可为空。
     */
    private String taskId;
    /**
     * 每个文件的覆盖率明细，可供前端展示或继续聚合。
     */
    @Builder.Default
    private List<CoverageDetail> details = new ArrayList<>();
    /** 全部文件的加权覆盖率（totalMatchedLines / totalLines）。 */
    private double overallCoverage;
    /** 所有文件匹配得到的加权行数之和（Σ 相似度 × 行数）。 */
    private double totalMatchedLines;
    /** ΔO 在所有文件中贡献的总行数。 */
    private int totalLines;
}
