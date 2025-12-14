package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Strong模式覆盖率评估器
 * 基于业务特征锚点的强匹配算法
 * 采用平均分算法，废弃行数加权
 */
@Slf4j
@Component
public class StrongCoverageEvaluator {

    private final StrongBlockMapper blockMapper;
    private final com.example.migratediff.domain.coverage.optimization.CoverageOptimizationManager optimizationManager;

    @Autowired
    public StrongCoverageEvaluator(StrongBlockMapper blockMapper, 
                               com.example.migratediff.domain.coverage.optimization.CoverageOptimizationManager optimizationManager) {
        this.blockMapper = blockMapper;
        this.optimizationManager = optimizationManager;
    }

    /**
     * 计算单个文件的覆盖率（Strong模式）
     * 使用业务特征锚点和平均分算法
     * 
     * @param originFile ΔO的差异文件
     * @param targetFile ΔG的差异文件
     * @param threshold 展示阈值
     * @param criticalMissThreshold 关键丢失阈值
     * @return 含匹配统计与块列表的覆盖率明细
     */
    public CoverageDetail evaluateFile(DiffFile originFile, DiffFile targetFile, double threshold, double criticalMissThreshold) {
        List<DiffBlock> originBlocks = safeBlocks(originFile);
        List<DiffBlock> targetBlocks = safeBlocks(targetFile);

        if (originBlocks.isEmpty()) {
            return buildEmptyCoverageDetail(originFile, targetFile, threshold);
        }

        // 使用Strong模式块映射算法
        BlockMapping originalMapping = blockMapper.mapBlocksBestMatch(originBlocks, targetBlocks, 0.6);
        
        // 应用优化策略
        BlockMapping optimizedMapping = optimizationManager.optimizeMapping(originalMapping, originFile, targetFile);
        
        // 计算Strong模式的覆盖率指标
        return calculateStrongCoverageDetail(originFile, optimizedMapping, threshold, criticalMissThreshold);
    }

    /**
     * 计算Strong模式的覆盖率明细
     * 新公式：AverageScore = Sum(BlockScores) / BlockCount
     */
    private CoverageDetail calculateStrongCoverageDetail(DiffFile originFile, BlockMapping mapping, 
                                                double displayThreshold, double criticalMissThreshold) {
        // 调整关键丢失阈值从默认值到0.6，提高严格度
        if (criticalMissThreshold < 0.6) {
            criticalMissThreshold = 0.6;
        }
        List<DiffBlock> allBlocks = new ArrayList<>();
        List<DiffBlock> highSimilarityBlocks = new ArrayList<>();
        List<DiffBlock> lowSimilarityBlocks = new ArrayList<>();
        
        double totalScore = 0.0;
        int blockCount = 0;
        int criticalMissCount = 0;
        
        // 处理已匹配的块
        for (DiffBlock oBlock : mapping.getMatchedOracleBlocks()) {
            double similarity = mapping.getSimilarity(oBlock);
            totalScore += similarity;
            blockCount++;
            
            allBlocks.add(oBlock);
            
            if (similarity >= displayThreshold) {
                highSimilarityBlocks.add(oBlock);
            } else {
                lowSimilarityBlocks.add(oBlock);
            }
            
            // 统计关键丢失（Score < 0.3）
            if (similarity < criticalMissThreshold) {
                criticalMissCount++;
            }
        }
        
        // 处理未匹配的块（相似度为0）
        for (DiffBlock oBlock : mapping.getUnmatchedOracle()) {
            totalScore += 0.0;
            blockCount++;
            
            allBlocks.add(oBlock);
            lowSimilarityBlocks.add(oBlock);
            criticalMissCount++; // 未匹配也算关键丢失
        }
        
        // Strong模式新公式：平均分算法
        double averageScore = blockCount == 0 ? 1.0 : totalScore / blockCount;
        
        return CoverageDetail.builder()
                .filePath(resolveFilePath(originFile, null))
                .coverage(averageScore) // Strong模式使用平均分作为覆盖率
                .matchedLines(totalScore)  // 保持兼容性，实际是总分
                .totalLines(blockCount)    // 保持兼容性，实际是块数
                .matchedBlocks(highSimilarityBlocks)
                .unmatchedBlocks(lowSimilarityBlocks)
                .blockScore(averageScore)  // 新增字段
                .isCriticalMiss(criticalMissCount > 0)  // 新增字段
                .criticalMissCount(criticalMissCount)   // 新增字段
                .build();
    }

    /**
     * 构建空文件的覆盖率明细
     */
    private CoverageDetail buildEmptyCoverageDetail(DiffFile originFile, DiffFile targetFile, double threshold) {
        String filePath = resolveFilePath(originFile, targetFile);
        
        // 空文件视为完全覆盖
        return CoverageDetail.builder()
                .filePath(filePath)
                .coverage(1.0)
                .matchedLines(0.0)
                .totalLines(0)
                .matchedBlocks(Collections.emptyList())
                .unmatchedBlocks(Collections.emptyList())
                .blockScore(1.0)
                .isCriticalMiss(false)
                .criticalMissCount(0)
                .build();
    }

    /**
     * 安全获取块列表
     */
    private List<DiffBlock> safeBlocks(DiffFile file) {
        if (file == null || file.getBlocks() == null) {
            return Collections.emptyList();
        }
        List<DiffBlock> filtered = new ArrayList<>();
        for (DiffBlock block : file.getBlocks()) {
            if (block != null) {
                filtered.add(block);
            }
        }
        return filtered;
    }

    /**
     * 解析文件路径
     */
    private String resolveFilePath(DiffFile deltaO, DiffFile deltaG) {
        if (deltaO != null && deltaO.getRelativePath() != null) {
            return deltaO.getRelativePath();
        }
        if (deltaG != null) {
            return deltaG.getRelativePath();
        }
        return "";
    }
}
