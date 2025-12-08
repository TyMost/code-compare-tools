package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strong模式块映射器
 * 基于业务特征锚点的全局最佳匹配算法
 * 不使用位置窗口，采用全局搜索和业务特征重合度评分
 */
@Component
public class StrongBlockMapper {

    /**
     * 创建ΔO和ΔG之间的块映射关系
     * 使用业务特征锚点的全局最佳匹配算法
     * 
     * @param oBlocks ΔO的块列表
     * @param gBlocks ΔG的块列表
     * @param minThreshold 最小相似度阈值
     * @return 块映射结果
     */
    public BlockMapping mapBlocksBestMatch(List<DiffBlock> oBlocks, List<DiffBlock> gBlocks, double minThreshold) {
        BlockMapping mapping = BlockMapping.builder().build();
        
        if (oBlocks == null || oBlocks.isEmpty()) {
            return mapping;
        }
        
        if (gBlocks == null || gBlocks.isEmpty()) {
            // 如果ΔG为空，所有ΔO块都标记为未匹配
            for (DiffBlock oBlock : oBlocks) {
                mapping.addUnmatchedOracle(oBlock);
            }
            return mapping;
        }
        
        // 预计算所有块的业务特征tokens
        Map<DiffBlock, Set<String>> oTokenMap = precomputeBusinessTokens(oBlocks);
        Map<DiffBlock, Set<String>> gTokenMap = precomputeBusinessTokens(gBlocks);
        
        // 为每个ΔO块寻找最佳匹配的ΔG块
        List<DiffBlock> usedGBlocks = new ArrayList<>();
        for (DiffBlock oBlock : oBlocks) {
            Set<String> oTokens = oTokenMap.get(oBlock);
            if (oTokens == null || oTokens.isEmpty()) {
                mapping.addUnmatchedOracle(oBlock);
                continue;
            }
            
            MatchResult bestMatch = findBestBusinessMatch(oBlock, oTokens, gBlocks, gTokenMap, usedGBlocks);
            
            if (bestMatch.gBlock != null && bestMatch.similarity >= minThreshold) {
                mapping.addMatch(oBlock, bestMatch.gBlock, bestMatch.similarity);
                usedGBlocks.add(bestMatch.gBlock);
            } else {
                mapping.addUnmatchedOracle(oBlock);
            }
        }
        
        // 标记未使用的ΔG块
        markUnusedGaussBlocks(mapping, gBlocks, usedGBlocks);
        
        return mapping;
    }
    
    /**
     * 预计算块的业务特征tokens
     */
    private Map<DiffBlock, Set<String>> precomputeBusinessTokens(List<DiffBlock> blocks) {
        Map<DiffBlock, Set<String>> tokenMap = new HashMap<>();
        
        for (DiffBlock block : blocks) {
            if (block == null) {
                continue;
            }
            
            // 合并from和to内容的业务特征
            String fromContent = block.getContentFrom();
            String toContent = block.getContentTo();
            String combinedContent = combineContent(fromContent, toContent);
            
            Set<String> businessTokens = CoverageUtils.tokenizeBusinessFeatures(combinedContent);
            tokenMap.put(block, businessTokens);
        }
        
        return tokenMap;
    }
    
    /**
     * 合并from和to内容
     */
    private String combineContent(String fromContent, String toContent) {
        if (fromContent == null && toContent == null) {
            return "";
        }
        
        StringBuilder combined = new StringBuilder();
        if (fromContent != null && !fromContent.trim().isEmpty()) {
            combined.append(fromContent);
        }
        if (toContent != null && !toContent.trim().isEmpty()) {
            if (combined.length() > 0) {
                combined.append("\n");
            }
            combined.append(toContent);
        }
        
        return combined.toString();
    }
    
    /**
     * 为ΔO块寻找最佳业务特征匹配的ΔG块
     */
    private MatchResult findBestBusinessMatch(DiffBlock oBlock, Set<String> oTokens, 
                                          List<DiffBlock> gBlocks, 
                                          Map<DiffBlock, Set<String>> gTokenMap,
                                          List<DiffBlock> usedGBlocks) {
        DiffBlock bestMatch = null;
        double bestSimilarity = 0.0;
        
        for (DiffBlock gBlock : gBlocks) {
            if (usedGBlocks.contains(gBlock)) {
                continue; // 跳过已使用的块
            }
            
            Set<String> gTokens = gTokenMap.get(gBlock);
            if (gTokens == null || gTokens.isEmpty()) {
                continue;
            }
            
            // 计算业务特征重合度：intersection / oTokens
            double similarity = calculateBusinessFeatureOverlap(oTokens, gTokens);
            
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = gBlock;
            }
        }
        
        return new MatchResult(bestMatch, bestSimilarity);
    }
    
    /**
     * 计算业务特征重合度
     * Score = (intersection of tokens).size() / (oTokens).size()
     */
    private double calculateBusinessFeatureOverlap(Set<String> oTokens, Set<String> gTokens) {
        if (oTokens == null || oTokens.isEmpty()) {
            return 0.0;
        }
        
        if (gTokens == null || gTokens.isEmpty()) {
            return 0.0;
        }
        
        // 计算交集
        Set<String> intersection = new java.util.HashSet<>(oTokens);
        intersection.retainAll(gTokens);
        
        return (double) intersection.size() / oTokens.size();
    }
    
    /**
     * 标记未使用的ΔG块
     */
    private void markUnusedGaussBlocks(BlockMapping mapping, List<DiffBlock> gBlocks, List<DiffBlock> usedGBlocks) {
        for (DiffBlock gBlock : gBlocks) {
            if (!usedGBlocks.contains(gBlock)) {
                mapping.addUnmatchedGauss(gBlock);
            }
        }
    }
    
    /**
     * 匹配结果封装类
     */
    private static class MatchResult {
        final DiffBlock gBlock;
        final double similarity;
        
        MatchResult(DiffBlock gBlock, double similarity) {
            this.gBlock = gBlock;
            this.similarity = similarity;
        }
    }
}
