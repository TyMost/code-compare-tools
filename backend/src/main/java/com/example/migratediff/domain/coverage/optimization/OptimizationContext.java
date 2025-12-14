package com.example.migratediff.domain.coverage.optimization;

import com.example.migratediff.domain.coverage.BlockMapping;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 优化上下文
 * 包含执行覆盖率优化策略所需的所有信息
 */
@Data
@Builder
public class OptimizationContext {
    
    /**
     * 原始差异块（来自ΔO）
     */
    private DiffBlock originBlock;
    
    /**
     * 目标差异块（来自ΔG）
     */
    private DiffBlock targetBlock;
    
    /**
     * 原始差异文件
     */
    private DiffFile originFile;
    
    /**
     * 目标差异文件
     */
    private DiffFile targetFile;
    
    /**
     * 当前相似度（优化前的相似度）
     */
    private double currentSimilarity;
    
    /**
     * 原始块映射关系
     */
    private BlockMapping originalMapping;
    
    /**
     * 额外的上下文数据
     * 用于策略之间传递信息
     */
    @Builder.Default
    private Map<String, Object> additionalData;
    
    /**
     * 获取额外数据
     * 
     * @param key 数据键
     * @param defaultValue 默认值
     * @param <T> 数据类型
     * @return 数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAdditionalData(String key, T defaultValue) {
        if (additionalData == null) {
            return defaultValue;
        }
        Object value = additionalData.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 设置额外数据
     * 
     * @param key 数据键
     * @param value 数据值
     */
    public void setAdditionalData(String key, Object value) {
        if (additionalData == null) {
            additionalData = new java.util.HashMap<>();
        }
        additionalData.put(key, value);
    }
    
    /**
     * 检查是否为完美匹配
     * 
     * @return 是否为完美匹配
     */
    public boolean isPerfectMatch() {
        return currentSimilarity >= 1.0;
    }
    
    /**
     * 检查是否需要优化
     * 
     * @return 是否需要优化
     */
    public boolean needsOptimization() {
        return currentSimilarity < 1.0;
    }
    
    /**
     * 检查是否为发现模式（targetBlock为null）
     * 
     * @return 是否为发现模式
     */
    public boolean isDiscoveryMode() {
        return targetBlock == null && originBlock != null;
    }
}
