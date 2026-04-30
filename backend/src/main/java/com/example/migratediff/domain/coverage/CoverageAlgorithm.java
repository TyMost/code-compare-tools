package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;

import java.util.List;

/**
 * 覆盖率算法策略接口
 * 支持多种算法实现：Legacy和Strong模式
 */
public interface CoverageAlgorithm {
    
    /**
     * 计算覆盖率汇总
     * 
     * @param context 计算上下文
     * @return 覆盖率汇总结果
     */
    CoverageSummary calculate(CalculationContext context);
    
    /**
     * 创建块映射关系
     * 
     * @param oBlocks ΔO的块列表
     * @param gBlocks ΔG的块列表
     * @return 块映射结果
     */
    BlockMapping createBlockMapping(List<DiffBlock> oBlocks, List<DiffBlock> gBlocks);
    
    /**
     * 获取算法名称
     * 
     * @return 算法名称
     */
    String getAlgorithmName();
}
