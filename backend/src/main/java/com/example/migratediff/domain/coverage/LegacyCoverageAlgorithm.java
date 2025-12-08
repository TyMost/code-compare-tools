package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy模式覆盖率算法实现
 * 保持原有算法逻辑完全不变
 */
@Component("legacy")
public class LegacyCoverageAlgorithm implements CoverageAlgorithm {

    private final CoverageEvaluator coverageEvaluator;

    @Autowired
    public LegacyCoverageAlgorithm(CoverageEvaluator coverageEvaluator) {
        this.coverageEvaluator = coverageEvaluator;
    }

    @Override
    public CoverageSummary calculate(CalculationContext context) {
        if (context == null) {
            return CoverageSummary.builder().build();
        }

        List<DiffFile> safeDeltaO = context.getDeltaOFiles() != null ? 
            context.getDeltaOFiles() : Collections.emptyList();
        Map<String, DiffFile> targetIndex = indexByPath(context.getDeltaGFiles());

        java.util.List<CoverageDetail> details = new java.util.ArrayList<>();
        double totalMatchedLines = 0D;
        int totalLines = 0;

        for (DiffFile originFile : safeDeltaO) {
            if (originFile == null) {
                continue;
            }
            DiffFile candidate = targetIndex.getOrDefault(originFile.getRelativePath(), null);
            // 使用原有的Legacy覆盖率计算方法
            CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(
                    originFile, candidate, context.getDisplayThreshold());
            details.add(detail);
            totalMatchedLines += detail.getMatchedLines();
            totalLines += detail.getTotalLines();
        }

        double overallCoverage = totalLines == 0 ? 1D : (double) totalMatchedLines / (double) totalLines;
        CoverageSummary summary = CoverageSummary.builder()
                .overallCoverage(overallCoverage)
                .totalMatchedLines(totalMatchedLines)
                .totalLines(totalLines)
                .build();
        summary.getDetails().addAll(details);
        
        return summary;
    }

    @Override
    public BlockMapping createBlockMapping(List<DiffBlock> oBlocks, List<DiffBlock> gBlocks) {
        // Legacy模式使用原有的OrderAwareBlockMapper
        // 这里可以通过coverageEvaluator获取映射关系
        return BlockMapping.builder().build();
    }

    @Override
    public String getAlgorithmName() {
        return "legacy";
    }

    /**
     * 按路径索引文件
     */
    private Map<String, DiffFile> indexByPath(List<DiffFile> files) {
        Map<String, DiffFile> index = new HashMap<>();
        if (files == null) {
            return index;
        }
        for (DiffFile file : files) {
            if (file == null) {
                continue;
            }
            String relativePath = file.getRelativePath();
            if (relativePath != null) {
                index.put(relativePath, file);
            }
        }
        return index;
    }
}
