package com.example.migratediff.domain.coverage.optimization;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.shared.utils.CoverageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 噪音过滤策略
 * 通过过滤import语句、注释等噪音来重新计算相似度
 */
@Slf4j
@Component
public class NoiseFilterStrategy implements CoverageOptimizationStrategy {
    
    @Override
    public OptimizationResult optimize(OptimizationContext context) {
        long startTime = System.currentTimeMillis();
        double originalSimilarity = context.getCurrentSimilarity();
        
        try {
            // 获取原始块的组合内容
            String originContent = combineContent(context.getOriginBlock());
            String targetContent = combineContent(context.getTargetBlock());
            
            // 过滤噪音
            String filteredOriginContent = CoverageUtils.filterCodeNoise(originContent);
            String filteredTargetContent = CoverageUtils.filterCodeNoise(targetContent);
            
            // 计算过滤后的相似度
            double filteredSimilarity = calculateFilteredSimilarity(filteredOriginContent, filteredTargetContent);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 创建详细信息
            Map<String, Object> details = new HashMap<>();
            details.put("originalOriginLength", originContent.length());
            details.put("originalTargetLength", targetContent.length());
            details.put("filteredOriginLength", filteredOriginContent.length());
            details.put("filteredTargetLength", filteredTargetContent.length());
            details.put("originNoiseRatio", calculateNoiseRatio(originContent, filteredOriginContent));
            details.put("targetNoiseRatio", calculateNoiseRatio(targetContent, filteredTargetContent));
            
            // 判断是否有优化
            boolean isOptimized = filteredSimilarity > originalSimilarity;
            
            String reason;
            if (filteredSimilarity >= 1.0) {
                reason = "噪音过滤后完全匹配";
            } else if (isOptimized) {
                reason = "噪音过滤后相似度提升";
            } else {
                reason = "噪音过滤后相似度未提升";
            }
            
            log.debug("噪音过滤策略执行完成: 原始相似度={}, 过滤后相似度={}, 提升={}, 耗时={}ms", 
                     originalSimilarity, filteredSimilarity, filteredSimilarity - originalSimilarity, executionTime);
            
            if (isOptimized) {
                return OptimizationResult.createOptimized(originalSimilarity, filteredSimilarity, reason, details, executionTime);
            } else {
                return OptimizationResult.notOptimized(originalSimilarity, reason);
            }
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.warn("噪音过滤策略执行失败: {}", e.getMessage(), e);
            
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            
            return OptimizationResult.notOptimized(originalSimilarity, "噪音过滤策略执行失败: " + e.getMessage());
        }
    }
    
    @Override
    public String getStrategyName() {
        return "NOISE_FILTER";
    }
    
    @Override
    public int getPriority() {
        return 1; // 最高优先级
    }
    
    @Override
    public boolean supports(OptimizationContext context) {
        // 支持所有非完美匹配的情况
        return context != null && context.needsOptimization() && 
               context.getOriginBlock() != null;
    }
    
    /**
     * 合并块的from和to内容
     */
    private String combineContent(DiffBlock block) {
        if (block == null) {
            return "";
        }
        
        StringBuilder content = new StringBuilder();
        String fromContent = block.getContentFrom();
        String toContent = block.getContentTo();
        
        if (fromContent != null && !fromContent.trim().isEmpty()) {
            content.append(fromContent);
        }
        if (toContent != null && !toContent.trim().isEmpty()) {
            if (content.length() > 0) {
                content.append("\n");
            }
            content.append(toContent);
        }
        
        return content.toString();
    }
    
    /**
     * 计算过滤后的相似度
     * 使用业务特征token进行相似度计算
     */
    private double calculateFilteredSimilarity(String filteredOriginContent, String filteredTargetContent) {
        // 使用CoverageUtils的业务特征tokenization
        java.util.Set<String> originTokens = CoverageUtils.tokenizeBusinessFeatures(filteredOriginContent);
        java.util.Set<String> targetTokens = CoverageUtils.tokenizeBusinessFeatures(filteredTargetContent);
        
        if (originTokens.isEmpty() && targetTokens.isEmpty()) {
            return 1.0; // 都为空视为完全匹配
        }
        
        if (originTokens.isEmpty()) {
            return 0.0; // 原始为空，目标不为空，完全不匹配
        }
        
        // 计算召回率（Recall）: intersection / originTokens
        java.util.Set<String> intersection = new java.util.HashSet<>(originTokens);
        intersection.retainAll(targetTokens);
        
        return (double) intersection.size() / originTokens.size();
    }
    
    /**
     * 计算噪音比例
     */
    private double calculateNoiseRatio(String originalContent, String filteredContent) {
        if (originalContent == null || originalContent.isEmpty()) {
            return 0.0;
        }
        
        int originalLength = originalContent.length();
        int filteredLength = filteredContent != null ? filteredContent.length() : 0;
        
        return originalLength > 0 ? (double)(originalLength - filteredLength) / originalLength : 0.0;
    }
}
