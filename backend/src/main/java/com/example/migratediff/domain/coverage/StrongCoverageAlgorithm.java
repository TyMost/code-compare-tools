package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strong模式覆盖率算法实现
 * 基于业务特征锚点的强匹配算法
 */
@Component("strong")
public class StrongCoverageAlgorithm implements CoverageAlgorithm {

    private final StrongCoverageEvaluator evaluator;

    @Autowired
    public StrongCoverageAlgorithm(StrongCoverageEvaluator evaluator) {
        this.evaluator = evaluator;
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
        int totalCriticalMissCount = 0;

        // 算法配置
        CalculationContext.AlgorithmConfig config = context.getConfig();
        double displayThreshold = context.getDisplayThreshold();
        double criticalMissThreshold = config != null ? config.getCriticalMissThreshold() : 0.3;

        for (DiffFile originFile : safeDeltaO) {
            if (originFile == null) {
                continue;
            }
            
            DiffFile candidate = targetIndex.getOrDefault(originFile.getRelativePath(), null);
            // 使用Strong模式覆盖率计算
            CoverageDetail detail = evaluator.evaluateFile(originFile, candidate, displayThreshold, criticalMissThreshold);
            details.add(detail);
            
            totalMatchedLines += detail.getMatchedLines();
            totalLines += detail.getTotalLines();
            totalCriticalMissCount += detail.getCriticalMissCount();
        }

        // Strong模式使用平均分作为整体覆盖率
        double overallCoverage = totalLines == 0 ? 1D : (double) totalMatchedLines / (double) totalLines;
        
        CoverageSummary summary = CoverageSummary.builder()
                .overallCoverage(overallCoverage)
                .totalMatchedLines(totalMatchedLines)
                .totalLines(totalLines)
                .build();
        summary.getDetails().addAll(details);
        
        // 设置Strong模式特有的指标（如果扩展了CoverageSummary）
        // summary.setCriticalMissCount(totalCriticalMissCount);
        
        return summary;
    }

    @Override
    public BlockMapping createBlockMapping(List<DiffBlock> oBlocks, List<DiffBlock> gBlocks) {
        // 这里可以创建StrongBlockMapper实例并调用
        // 为了简单，返回基本映射，实际使用时依赖evaluator内部处理
        return BlockMapping.builder().build();
    }

    @Override
    public String getAlgorithmName() {
        return "strong";
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
