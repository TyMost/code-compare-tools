package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 覆盖率计算的上下文对象
 * 包含算法计算所需的所有参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationContext {
    
    /**
     * 应用层下发的任务标识，可为空
     */
    private String taskId;
    
    /**
     * ΔO 的差异文件列表
     */
    private List<DiffFile> deltaOFiles;
    
    /**
     * ΔG 的差异文件列表
     */
    private List<DiffFile> deltaGFiles;
    
    /**
     * 展示层阈值（仅用于UI展示）
     */
    private double displayThreshold;
    
    /**
     * 是否持久化结果
     */
    private boolean persistResult;
    
    /**
     * 算法配置参数
     */
    private AlgorithmConfig config;
    
    /**
     * 算法配置参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlgorithmConfig {
        
        /**
         * 是否启用噪音过滤
         */
        private boolean enableNoiseFiltering;
        
        /**
         * 是否跳过未匹配的纯噪音块
         */
        private boolean skipUnmatchedNoiseBlocks;
        
        /**
         * 位置窗口大小（Legacy模式使用）
         */
        private int positionWindow;
        
        /**
         * 最小相似度阈值（Strong模式使用）
         */
        private double minSimilarityThreshold;
        
        /**
         * 关键丢失阈值（Strong模式使用）
         */
        private double criticalMissThreshold;
    }
}
