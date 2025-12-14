package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.coverage.optimization.OptimizationResult;
import com.example.migratediff.domain.diff.DiffBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 块映射结果，表示ΔO和ΔG之间的一一对应关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockMapping {
    
    /** ΔO块到ΔG块的映射关系 */
    @Builder.Default
    private Map<DiffBlock, DiffBlock> oracleToGauss = new HashMap<>();
    
    /** 映射的相似度分数 */
    @Builder.Default
    private Map<DiffBlock, Double> similarities = new HashMap<>();
    
    /** ΔO中未匹配的块 */
    @Builder.Default
    private List<DiffBlock> unmatchedOracle = new ArrayList<>();
    
    /** ΔG中未匹配的块 */
    @Builder.Default
    private List<DiffBlock> unmatchedGauss = new ArrayList<>();
    
    /** 匹配关系的详细信息（用于前端显示） */
    @Builder.Default
    private List<MatchDetail> matchDetails = new ArrayList<>();
    
    /** 优化历史记录 */
    @Builder.Default
    private Map<DiffBlock, List<OptimizationResult>> optimizationHistory = new HashMap<>();
    
    /** 优化汇总信息 */
    private Object optimizationSummary;
    
    /**
     * 添加一个匹配关系
     */
    public void addMatch(DiffBlock oracleBlock, DiffBlock gaussBlock, double similarity) {
        oracleToGauss.put(oracleBlock, gaussBlock);
        similarities.put(oracleBlock, similarity);
        // 同时更新matchDetails
        matchDetails.add(new MatchDetail(oracleBlock, gaussBlock, similarity));
    }
    
    /**
     * 添加一个匹配关系（仅块映射，不包含相似度）
     */
    public void addMatch(DiffBlock oracleBlock, DiffBlock gaussBlock) {
        oracleToGauss.put(oracleBlock, gaussBlock);
    }
    
    /**
     * 添加相似度信息
     */
    public void addSimilarity(DiffBlock oracleBlock, Double similarity) {
        similarities.put(oracleBlock, similarity);
    }
    
    /**
     * 添加未匹配的ΔO块
     */
    public void addUnmatchedOracle(DiffBlock oracleBlock) {
        if (!oracleToGauss.containsKey(oracleBlock)) {
            unmatchedOracle.add(oracleBlock);
        }
    }
    
    /**
     * 添加未匹配的ΔG块
     */
    public void addUnmatchedGauss(DiffBlock gaussBlock) {
        if (!oracleToGauss.containsValue(gaussBlock)) {
            unmatchedGauss.add(gaussBlock);
        }
    }
    
    /**
     * 获取ΔO块对应的ΔG块
     */
    public DiffBlock getGaussBlock(DiffBlock oracleBlock) {
        return oracleToGauss.get(oracleBlock);
    }
    
    /**
     * 获取匹配的相似度
     */
    public double getSimilarity(DiffBlock oracleBlock) {
        return similarities.getOrDefault(oracleBlock, 0.0);
    }
    
    /**
     * 更新相似度（用于优化后）
     */
    public void updateSimilarity(DiffBlock oracleBlock, double newSimilarity) {
        similarities.put(oracleBlock, newSimilarity);
        
        // 更新matchDetails中的相似度
        for (MatchDetail detail : matchDetails) {
            if (detail.getOracleBlock().equals(oracleBlock)) {
                detail.setSimilarity(newSimilarity);
                break;
            }
        }
    }
    
    /**
     * 获取覆盖率
     */
    public double getCoverage() {
        int totalOracle = oracleToGauss.size() + unmatchedOracle.size();
        return totalOracle == 0 ? 1.0 : (double)oracleToGauss.size() / totalOracle;
    }
    
    /**
     * 获取匹配数量
     */
    public int getMatchedCount() {
        return oracleToGauss.size();
    }
    
    /**
     * 获取ΔO总块数
     */
    public int getTotalOracleCount() {
        return oracleToGauss.size() + unmatchedOracle.size();
    }
    
    /**
     * 获取ΔG总块数
     */
    public int getTotalGaussCount() {
        return oracleToGauss.size() + unmatchedGauss.size();
    }
    
    /**
     * 获取所有匹配的ΔO块
     */
    public List<DiffBlock> getMatchedOracleBlocks() {
        return new ArrayList<>(oracleToGauss.keySet());
    }
    
    /**
     * 获取所有匹配的ΔG块
     */
    public List<DiffBlock> getMatchedGaussBlocks() {
        return new ArrayList<>(oracleToGauss.values());
    }
    
    /**
     * 检查是否有匹配关系
     */
    public boolean hasMatch(DiffBlock oracleBlock) {
        return oracleToGauss.containsKey(oracleBlock);
    }
    
    /**
     * 获取匹配关系的详细信息
     * @deprecated 使用 matchDetails 字段替代
     */
    @Deprecated
    public List<MatchDetail> getMatchDetails() {
        return new ArrayList<>(matchDetails);
    }
    
    /**
     * 添加优化结果记录
     */
    public void addOptimizationResult(DiffBlock block, OptimizationResult result) {
        optimizationHistory.computeIfAbsent(block, k -> new ArrayList<>()).add(result);
    }
    
    /**
     * 获取块的优化历史
     */
    public List<OptimizationResult> getOptimizationHistory(DiffBlock block) {
        return optimizationHistory.getOrDefault(block, new ArrayList<>());
    }
    
    /**
     * 添加发现的匹配关系
     * 用于发现模式中创建的新匹配
     */
    public void addDiscoveredMatch(DiffBlock oracleBlock, DiffBlock gaussBlock, double similarity) {
        addMatch(oracleBlock, gaussBlock, similarity);
        // 从未匹配列表中移除
        if (unmatchedOracle.contains(oracleBlock)) {
            unmatchedOracle.remove(oracleBlock);
        }
        if (unmatchedGauss.contains(gaussBlock)) {
            unmatchedGauss.remove(gaussBlock);
        }
    }
    
    /**
     * 检查匹配是否为发现的
     */
    public boolean isDiscoveredMatch(DiffBlock oracleBlock) {
        // 检查优化历史中是否有发现记录
        List<OptimizationResult> history = optimizationHistory.get(oracleBlock);
        if (history != null) {
            for (OptimizationResult result : history) {
                if (result.getDetails() != null && 
                    result.getDetails().containsKey("discovered") && 
                    Boolean.TRUE.equals(result.getDetails().get("discovered"))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 复制块映射（创建深拷贝用于优化）
     */
    public BlockMapping copy() {
        BlockMapping copy = BlockMapping.builder()
                .oracleToGauss(new HashMap<>(oracleToGauss))
                .similarities(new HashMap<>(similarities))
                .unmatchedOracle(new ArrayList<>(unmatchedOracle))
                .unmatchedGauss(new ArrayList<>(unmatchedGauss))
                .matchDetails(new ArrayList<>(matchDetails))
                .optimizationHistory(new HashMap<>())
                .optimizationSummary(optimizationSummary)
                .build();
        
        // 深拷贝优化历史
        optimizationHistory.forEach((block, results) -> {
            copy.optimizationHistory.put(block, new ArrayList<>(results));
        });
        
        return copy;
    }
    
    /**
     * 匹配详情
     */
    @Data
    @AllArgsConstructor
    public static class MatchDetail {
        private DiffBlock oracleBlock;
        private DiffBlock gaussBlock;
        private double similarity;
    }
}
