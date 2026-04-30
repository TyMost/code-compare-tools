package com.example.migratediff.domain.coverage.optimization;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 优化结果
 * 包含策略执行后的结果信息
 */
@Data
@Builder
public class OptimizationResult {
    
    /**
     * 优化后的相似度
     */
    private double optimizedSimilarity;
    
    /**
     * 优化原因描述
     */
    private String reason;
    
    /**
     * 是否进行了优化（相似度是否有提升）
     */
    private boolean isOptimized;
    
    /**
     * 优化详细信息
     * 包含策略执行过程中的详细数据
     */
    @Builder.Default
    private Map<String, Object> details;
    
    /**
     * 策略执行耗时（毫秒）
     */
    private long executionTimeMs;
    
    /**
     * 原始相似度
     */
    private double originalSimilarity;
    
    /**
     * 相似度提升幅度
     */
    private double improvement;
    
    /**
     * 是否为完美匹配
     */
    private boolean isPerfectMatch;
    
    /**
     * 获取相似度提升幅度
     * 
     * @return 提升幅度
     */
    public double getImprovement() {
        return optimizedSimilarity - originalSimilarity;
    }
    
    /**
     * 检查是否为完美匹配
     * 
     * @return 是否为完美匹配
     */
    public boolean isPerfectMatch() {
        return optimizedSimilarity >= 1.0;
    }
    
    /**
     * 获取详细信息的字符串表示
     * 
     * @return 详细信息
     */
    public String getDetailsString() {
        if (details == null || details.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        details.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(key).append("=").append(value);
        });
        return sb.toString();
    }
    
    /**
     * 创建未优化的结果
     * 
     * @param originalSimilarity 原始相似度
     * @param reason 未优化的原因
     * @return 优化结果
     */
    public static OptimizationResult notOptimized(double originalSimilarity, String reason) {
        return OptimizationResult.builder()
                .optimizedSimilarity(originalSimilarity)
                .originalSimilarity(originalSimilarity)
                .reason(reason)
                .isOptimized(false)
                .isPerfectMatch(originalSimilarity >= 1.0)
                .build();
    }
    
    /**
     * 创建优化的结果
     * 
     * @param originalSimilarity 原始相似度
     * @param optimizedSimilarity 优化后相似度
     * @param reason 优化原因
     * @param details 详细信息
     * @param executionTimeMs 执行时间
     * @return 优化结果
     */
    public static OptimizationResult createOptimized(double originalSimilarity, double optimizedSimilarity, 
                                            String reason, Map<String, Object> details, long executionTimeMs) {
        return OptimizationResult.builder()
                .optimizedSimilarity(optimizedSimilarity)
                .originalSimilarity(originalSimilarity)
                .reason(reason)
                .isOptimized(true)
                .details(details)
                .executionTimeMs(executionTimeMs)
                .isPerfectMatch(optimizedSimilarity >= 1.0)
                .build();
    }
    
    /**
     * 创建优化的结果（兼容测试用）
     * 
     * @param originalSimilarity 原始相似度
     * @param optimizedSimilarity 优化后相似度
     * @param improvement 改进幅度
     * @param reason 优化原因
     * @param executionTimeMs 执行时间
     * @param details 详细信息
     * @return 优化结果
     */
    public static OptimizationResult optimized(double originalSimilarity, double optimizedSimilarity, 
                                      double improvement, String reason, long executionTimeMs, Map<String, Object> details) {
        return OptimizationResult.builder()
                .optimizedSimilarity(optimizedSimilarity)
                .originalSimilarity(originalSimilarity)
                .reason(reason)
                .isOptimized(true)
                .details(details)
                .executionTimeMs(executionTimeMs)
                .isPerfectMatch(optimizedSimilarity >= 1.0)
                .build();
    }
}
