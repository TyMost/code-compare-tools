package com.example.migratediff.domain.coverage.optimization.v2;

import lombok.extern.slf4j.Slf4j;

/**
 * 超时管理器
 * 管理Discover和Verify阶段的超时控制
 */
@Slf4j
public class TimeoutManager {
    
    private final long discoverTimeoutMs;
    private final long verifyTimeoutMs;
    private final long totalTimeoutMs;
    
    private long discoverStartTime = 0;
    private long verifyStartTime = 0;
    
    /**
     * 构造函数
     * 
     * @param config 性能配置
     */
    public TimeoutManager(PerformanceConfig config) {
        this.discoverTimeoutMs = config.getDiscoverTimeoutMs();
        this.verifyTimeoutMs = config.getVerifyTimeoutMs();
        this.totalTimeoutMs = config.getTotalTimeoutMs();
    }
    
    /**
     * 开始发现阶段计时
     */
    public void startDiscoverPhase() {
        this.discoverStartTime = System.currentTimeMillis();
        log.debug("发现阶段计时开始");
    }
    
    /**
     * 开始验证阶段计时
     */
    public void startVerifyPhase() {
        this.verifyStartTime = System.currentTimeMillis();
        log.debug("验证阶段计时开始");
    }
    
    /**
     * 检查发现阶段是否超时
     * 
     * @param startTime 开始时间
     * @return 是否超时
     */
    public boolean isDiscoverTimeout(long startTime) {
        return (System.currentTimeMillis() - startTime) > discoverTimeoutMs;
    }
    
    /**
     * 检查验证阶段是否超时
     * 
     * @param startTime 开始时间
     * @return 是否超时
     */
    public boolean isVerifyTimeout(long startTime) {
        return (System.currentTimeMillis() - startTime) > verifyTimeoutMs;
    }
    
    /**
     * 检查总执行时间是否超时
     * 
     * @param totalStartTime 总开始时间
     * @return 是否超时
     */
    public boolean isTotalTimeout(long totalStartTime) {
        return (System.currentTimeMillis() - totalStartTime) > totalTimeoutMs;
    }
    
    /**
     * 记录超时事件
     * 
     * @param phase 阶段名称
     */
    public void recordTimeout(String phase) {
        log.warn("阶段执行超时: {}", phase);
    }
    
    /**
     * 获取剩余发现时间
     * 
     * @return 剩余时间（毫秒）
     */
    public long getRemainingDiscoverTime() {
        long elapsed = System.currentTimeMillis() - discoverStartTime;
        return Math.max(0, discoverTimeoutMs - elapsed);
    }
    
    /**
     * 获取剩余验证时间
     * 
     * @return 剩余时间（毫秒）
     */
    public long getRemainingVerifyTime() {
        long elapsed = System.currentTimeMillis() - verifyStartTime;
        return Math.max(0, verifyTimeoutMs - elapsed);
    }
    
    /**
     * 性能配置
     */
    public static class PerformanceConfig {
        private long discoverTimeoutMs = 3000;
        private long verifyTimeoutMs = 1000;
        private long totalTimeoutMs = 5000;
        
        // Getters and setters
        public long getDiscoverTimeoutMs() { return discoverTimeoutMs; }
        public void setDiscoverTimeoutMs(long discoverTimeoutMs) { this.discoverTimeoutMs = discoverTimeoutMs; }
        
        public long getVerifyTimeoutMs() { return verifyTimeoutMs; }
        public void setVerifyTimeoutMs(long verifyTimeoutMs) { this.verifyTimeoutMs = verifyTimeoutMs; }
        
        public long getTotalTimeoutMs() { return totalTimeoutMs; }
        public void setTotalTimeoutMs(long totalTimeoutMs) { this.totalTimeoutMs = totalTimeoutMs; }
    }
}
