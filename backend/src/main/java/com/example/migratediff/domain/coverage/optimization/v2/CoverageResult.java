package com.example.migratediff.domain.coverage.optimization.v2;

import lombok.Builder;
import lombok.Data;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discover → Verify 双阶段覆盖率结果
 * 表示origin内容是否被target文件覆盖的评估结果
 */
@Data
@Builder
public class CoverageResult {
    
    /**
     * 覆盖等级
     */
    public enum CoverageGrade {
        /**
         * 强命中：覆盖率 >= 0.8
         */
        HIT,
        
        /**
         * 弱命中：覆盖率 >= 0.4
         */
        WEAK_HIT,
        
        /**
         * 未命中：覆盖率 < 0.4
         */
        MISS
    }
    
    /**
     * 覆盖等级
     */
    private CoverageGrade grade;
    
    /**
     * 覆盖率（0-1）
     */
    private double coverageRate;
    
    /**
     * 最优候选代码段
     * 即使是MISS也必须提供最优候选
     */
    private CandidateSegment bestCandidate;
    
    /**
     * 所有候选代码段（最多10个）
     */
    private List<CandidateSegment> candidates;
    
    /**
     * 发现阶段耗时（毫秒）
     */
    private long discoverTimeMs;
    
    /**
     * 验证阶段耗时（毫秒）
     */
    private long verifyTimeMs;
    
    /**
     * 是否发生超时
     */
    private boolean timeout;
    
    /**
     * 发现阶段发现的候选总数
     */
    private int totalCandidatesDiscovered;
    
    /**
     * 结构过滤后剩余的候选数
     */
    private int candidatesAfterFilter;
    
    /**
     * 详细的性能指标
     */
    private Map<String, Object> performanceMetrics;
    
    /**
     * 检查是否为有效覆盖结果
     * 
     * @return 是否有有效的覆盖评估
     */
    public boolean hasCoverage() {
        return grade != null && bestCandidate != null;
    }
    
    /**
     * 检查是否为强命中
     * 
     * @return 是否为HIT
     */
    public boolean isHit() {
        return grade == CoverageGrade.HIT;
    }
    
    /**
     * 检查是否为弱命中
     * 
     * @return 是否为WEAK_HIT
     */
    public boolean isWeakHit() {
        return grade == CoverageGrade.WEAK_HIT;
    }
    
    /**
     * 检查是否为未命中
     * 
     * @return 是否为MISS
     */
    public boolean isMiss() {
        return grade == CoverageGrade.MISS;
    }
    
    /**
     * 获取总执行时间
     * 
     * @return discover + verify时间
     */
    public long getTotalTimeMs() {
        return discoverTimeMs + verifyTimeMs;
    }
    
    /**
     * 添加性能指标
     * 
     * @param key 指标键
     * @param value 指标值
     */
    public void addPerformanceMetric(String key, Object value) {
        if (performanceMetrics == null) {
            performanceMetrics = new HashMap<>();
        }
        performanceMetrics.put(key, value);
    }
}
