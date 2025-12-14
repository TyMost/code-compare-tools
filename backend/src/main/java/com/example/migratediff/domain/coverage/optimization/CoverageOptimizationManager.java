package com.example.migratediff.domain.coverage.optimization;

import com.example.migratediff.config.CoverageOptimizationConfig;
import com.example.migratediff.domain.coverage.BlockMapping;
import com.example.migratediff.domain.diff.DiffBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * 覆盖率优化管理器
 * 管理所有优化策略的执行，支持策略的可插拔设计
 */
@Slf4j
@Component
public class CoverageOptimizationManager {
    
    private final List<CoverageOptimizationStrategy> strategies;
    private final CoverageOptimizationConfig config;
    
    @Autowired
    public CoverageOptimizationManager(List<CoverageOptimizationStrategy> strategies,
                                   @Qualifier("coverageOptimizationConfig") CoverageOptimizationConfig config) {
        // 按优先级排序策略
        this.strategies = strategies.stream()
            .sorted(Comparator.comparingInt(CoverageOptimizationStrategy::getPriority))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        this.config = config;
        
        log.info("覆盖率优化管理器初始化完成，共加载{}个策略: {}", 
                this.strategies.size(),
                this.strategies.stream().map(CoverageOptimizationStrategy::getStrategyName).toArray());
        
        log.info("优化配置: 总开关={}, 噪音过滤={}", 
                config.isEnabled(), config.getNoiseFilter().isEnabled());
    }
    
    /**
     * 优化块映射
     * 对每个非完美匹配的块应用所有适用的优化策略
     * 
     * @param originalMapping 原始块映射
     * @param originFile 原始文件
     * @param targetFile 目标文件
     * @return 优化后的块映射
     */
    public BlockMapping optimizeMapping(BlockMapping originalMapping, 
                                       com.example.migratediff.domain.diff.DiffFile originFile, 
                                       com.example.migratediff.domain.diff.DiffFile targetFile) {
        
        if (!config.isEnabled()) {
            log.debug("覆盖率优化已禁用，返回原始映射");
            return originalMapping;
        }
        
        log.debug("开始优化块映射，原始匹配数: {}, 未匹配ΔO数: {}", 
                 originalMapping.getMatchedCount(), originalMapping.getUnmatchedOracle().size());
        
        BlockMapping optimizedMapping = createOptimizedMapping(originalMapping);
        OptimizationSummary summary = new OptimizationSummary();
        
        int optimizedBlocks = 0;
        int totalBlocks = 0;
        double totalImprovement = 0.0;
        
        // 处理已匹配的块
        for (DiffBlock oBlock : originalMapping.getMatchedOracleBlocks()) {
            totalBlocks++;
            double originalSimilarity = originalMapping.getSimilarity(oBlock);
            
            // 只优化非完美匹配
            if (originalSimilarity < 1.0) {
                OptimizationResult result = optimizeBlock(oBlock, originalMapping, originFile, targetFile);
                
                if (result.isOptimized()) {
                    optimizedMapping.updateSimilarity(oBlock, result.getOptimizedSimilarity());
                    optimizedBlocks++;
                    totalImprovement += result.getImprovement();
                    
                    log.debug("块优化成功: blockId={}, 原始相似度={}, 优化后相似度={}, 提升={}, 策略={}", 
                             getBlockId(oBlock), originalSimilarity, result.getOptimizedSimilarity(), 
                             result.getImprovement(), result.getReason());
                }
                
                // 记录优化历史
                recordOptimizationHistory(optimizedMapping, oBlock, result);
                summary.addBlockResult(getBlockId(oBlock), result);
            } else {
                // 完美匹配，记录为无需优化
                OptimizationResult result = OptimizationResult.notOptimized(originalSimilarity, "完美匹配，无需优化");
                recordOptimizationHistory(optimizedMapping, oBlock, result);
                summary.addBlockResult(getBlockId(oBlock), result);
            }
        }
        
        // 处理未匹配的块（发现模式）
        for (DiffBlock oBlock : originalMapping.getUnmatchedOracle()) {
            totalBlocks++;
            OptimizationResult result = processUnmatchedBlock(oBlock, originalMapping, originFile, targetFile);
            
            if (result.isOptimized() && result.getDetails() != null && result.getDetails().containsKey("discovered")) {
                // 发现了新匹配，创建匹配关系
                DiffBlock discoveredBlock = (DiffBlock) result.getDetails().get("discoveredBlock");
                optimizedMapping.addDiscoveredMatch(oBlock, discoveredBlock, result.getOptimizedSimilarity());
                
                log.debug("发现新匹配: blockId={}, 置信度={}, 文件={}", 
                         getBlockId(oBlock), result.getOptimizedSimilarity(),
                         originFile != null ? originFile.getRelativePath() : "unknown");
            }
            
            recordOptimizationHistory(optimizedMapping, oBlock, result);
            summary.addBlockResult(getBlockId(oBlock), result);
        }
        
        // 设置汇总信息
        summary.setTotalBlocks(totalBlocks);
        summary.setOptimizedBlocks(optimizedBlocks);
        summary.setTotalImprovement(totalImprovement);
        summary.setAverageImprovement(totalBlocks > 0 ? totalImprovement / totalBlocks : 0.0);
        optimizedMapping.setOptimizationSummary(summary);
        
        log.info("块映射优化完成: 总块数={}, 优化块数={}, 平均提升={}", 
                totalBlocks, optimizedBlocks, summary.getAverageImprovement());
        
        return optimizedMapping;
    }
    
    /**
     * 优化单个块
     * 依次应用所有适用的策略
     */
    private OptimizationResult optimizeBlock(DiffBlock oBlock, BlockMapping originalMapping,
                                          com.example.migratediff.domain.diff.DiffFile originFile,
                                          com.example.migratediff.domain.diff.DiffFile targetFile) {
        
        DiffBlock gBlock = originalMapping.getGaussBlock(oBlock);
        double originalSimilarity = originalMapping.getSimilarity(oBlock);
        
        // 创建优化上下文并填充Git信息
        Map<String, Object> additionalData = new java.util.HashMap<>();
        
        // 从DiffFile中提取Git信息
        if (originFile != null) {
            additionalData.put("originCommitHash", originFile.getCommitHash());
            additionalData.put("originRepoPath", originFile.getRepoPath());
        }
        if (targetFile != null) {
            additionalData.put("targetCommitHash", targetFile.getCommitHash());
            additionalData.put("targetRepoPath", targetFile.getRepoPath());
        }
        
        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(gBlock)
                .originFile(originFile)
                .targetFile(targetFile)
                .currentSimilarity(originalSimilarity)
                .originalMapping(originalMapping)
                .additionalData(additionalData)
                .build();
        
        OptimizationResult bestResult = OptimizationResult.notOptimized(originalSimilarity, "无可适用策略");
        
        // 依次执行策略
        for (CoverageOptimizationStrategy strategy : strategies) {
            if (!isStrategyEnabled(strategy) || !strategy.supports(context)) {
                continue;
            }
            
            try {
                OptimizationResult result = strategy.optimize(context);
                
                if (result.isOptimized() && result.getOptimizedSimilarity() > bestResult.getOptimizedSimilarity()) {
                    bestResult = result;
                    context.setCurrentSimilarity(result.getOptimizedSimilarity());
                    
                    log.debug("策略优化成功: 策略={}, 相似度提升={}", 
                             strategy.getStrategyName(), result.getImprovement());
                }
                
                // 如果已达到完美匹配，停止后续策略
                if (bestResult.isPerfectMatch()) {
                    log.debug("已达到完美匹配，停止后续策略执行");
                    break;
                }
                
            } catch (Exception e) {
                log.warn("策略执行失败: 策略={}, 错误={}", strategy.getStrategyName(), e.getMessage(), e);
            }
        }
        
        return bestResult;
    }
    
    /**
     * 检查策略是否启用
     */
    private boolean isStrategyEnabled(CoverageOptimizationStrategy strategy) {
        String strategyName = strategy.getStrategyName();
        
        switch (strategyName) {
            case "NOISE_FILTER":
                return config.getNoiseFilter().isEnabled();
            case "FULL_FILE_CONTEXT":
                return config.getFullFileContext().isEnabled();
            case "FULL_FILE_CONTEXT_V2":
                // V2策略使用独立的配置，默认启用
                return true;
            case "LLM_JUDGMENT":
                return config.getLlmJudgment().isEnabled();
            default:
                return true; // 默认启用未知策略
        }
    }
    
    /**
     * 获取块的唯一标识
     */
    private String getBlockId(DiffBlock block) {
        if (block == null) {
            return "null";
        }
        return String.format("%d_%d_%d_%d", 
                block.getStartLineFrom(), block.getEndLineFrom(),
                block.getStartLineTo(), block.getEndLineTo());
    }
    
    /**
     * 创建优化后的块映射副本
     */
    private BlockMapping createOptimizedMapping(BlockMapping originalMapping) {
        BlockMapping optimizedMapping = BlockMapping.builder().build();
        
        // 复制映射关系
        originalMapping.getOracleToGauss().forEach((oracleBlock, gaussBlock) -> {
            optimizedMapping.addMatch(oracleBlock, gaussBlock, originalMapping.getSimilarity(oracleBlock));
        });
        
        // 复制未匹配的块
        originalMapping.getUnmatchedOracle().forEach(optimizedMapping::addUnmatchedOracle);
        originalMapping.getUnmatchedGauss().forEach(optimizedMapping::addUnmatchedGauss);
        
        return optimizedMapping;
    }
    
    /**
     * 处理未匹配块（发现模式）
     */
    private OptimizationResult processUnmatchedBlock(DiffBlock oBlock, BlockMapping originalMapping,
                                              com.example.migratediff.domain.diff.DiffFile originFile,
                                              com.example.migratediff.domain.diff.DiffFile targetFile) {
        // 创建发现模式的优化上下文（targetBlock为null）
        Map<String, Object> additionalData = new java.util.HashMap<>();
        
        // 从DiffFile中提取Git信息
        if (originFile != null) {
            additionalData.put("originCommitHash", originFile.getCommitHash());
            additionalData.put("originRepoPath", originFile.getRepoPath());
        }
        if (targetFile != null) {
            additionalData.put("targetCommitHash", targetFile.getCommitHash());
            additionalData.put("targetRepoPath", targetFile.getRepoPath());
        }
        
        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(null) // 发现模式：targetBlock为null
                .originFile(originFile)
                .targetFile(targetFile)
                .currentSimilarity(0.0) // 未匹配块相似度为0
                .originalMapping(originalMapping)
                .additionalData(additionalData)
                .build();
        
        OptimizationResult bestResult = OptimizationResult.notOptimized(0.0, "无可适用策略");
        
        // 依次执行支持发现模式的策略
        for (CoverageOptimizationStrategy strategy : strategies) {
            if (!isStrategyEnabled(strategy) || !strategy.supports(context)) {
                continue;
            }
            
            try {
                OptimizationResult result = strategy.optimize(context);
                
                if (result.isOptimized() && result.getOptimizedSimilarity() > bestResult.getOptimizedSimilarity()) {
                    bestResult = result;
                    context.setCurrentSimilarity(result.getOptimizedSimilarity());
                    
                    log.debug("发现模式策略成功: 策略={}, 置信度={}", 
                             strategy.getStrategyName(), result.getOptimizedSimilarity());
                }
                
                // 如果已达到高置信度匹配，停止后续策略
                if (bestResult.getOptimizedSimilarity() >= 0.9) {
                    log.debug("已达到高置信度匹配，停止后续策略执行");
                    break;
                }
                
            } catch (Exception e) {
                log.warn("发现模式策略执行失败: 策略={}, 错误={}", strategy.getStrategyName(), e.getMessage(), e);
            }
        }
        
        return bestResult;
    }
    
    /**
     * 记录优化历史
     */
    private void recordOptimizationHistory(BlockMapping mapping, DiffBlock block, OptimizationResult result) {
        mapping.addOptimizationResult(block, result);
    }
    
    /**
     * 优化汇总信息
     */
    public static class OptimizationSummary {
        private int totalBlocks = 0;
        private int optimizedBlocks = 0;
        private double totalImprovement = 0.0;
        private double averageImprovement = 0.0;
        private Map<String, OptimizationResult> blockResults = new HashMap<>();
        
        public void addBlockResult(String blockId, OptimizationResult result) {
            blockResults.put(blockId, result);
        }
        
        // Getters and setters
        public int getTotalBlocks() { return totalBlocks; }
        public void setTotalBlocks(int totalBlocks) { this.totalBlocks = totalBlocks; }
        
        public int getOptimizedBlocks() { return optimizedBlocks; }
        public void setOptimizedBlocks(int optimizedBlocks) { this.optimizedBlocks = optimizedBlocks; }
        
        public double getTotalImprovement() { return totalImprovement; }
        public void setTotalImprovement(double totalImprovement) { this.totalImprovement = totalImprovement; }
        
        public double getAverageImprovement() { return averageImprovement; }
        public void setAverageImprovement(double averageImprovement) { this.averageImprovement = averageImprovement; }
        
        public Map<String, OptimizationResult> getBlockResults() { return blockResults; }
    }
}
