package com.example.migratediff.domain.coverage;

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
