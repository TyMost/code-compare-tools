package com.example.migratediff.domain.coverage.optimization;

/**
 * 覆盖率优化策略接口
 * 定义了覆盖率优化的标准接口，支持策略模式的可插拔设计
 */
public interface CoverageOptimizationStrategy {
    
    /**
     * 执行优化策略
     * 
     * @param context 优化上下文，包含原始块、目标块等必要信息
     * @return 优化结果，包含优化后的相似度、原因等信息
     */
    OptimizationResult optimize(OptimizationContext context);
    
    /**
     * 策略名称，用于标识和日志记录
     * 
     * @return 策略名称
     */
    String getStrategyName();
    
    /**
     * 策略优先级，数字越小优先级越高
     * 用于控制策略的执行顺序
     * 
     * @return 优先级数值
     */
    int getPriority();
    
    /**
     * 判断当前策略是否支持指定的优化上下文
     * 用于条件性策略执行
     * 
     * @param context 优化上下文
     * @return 是否支持
     */
    boolean supports(OptimizationContext context);
}
