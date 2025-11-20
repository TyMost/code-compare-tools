package com.example.migratediff.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的性能监控组件，用于跟踪关键操作的性能指标
 * 支持JDK 8兼容性实现，避免依赖外部监控库
 */
@Component
public class PerformanceMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceMonitor.class);

    private final boolean enableMonitoring;
    private final ConcurrentMap<String, OperationMetrics> metrics = new ConcurrentHashMap<>();

    public PerformanceMonitor(@Value("${migratediff.performance.monitoring.enabled:true}") boolean enableMonitoring) {
        this.enableMonitoring = enableMonitoring;
        if (enableMonitoring) {
            LOGGER.info("Performance monitoring enabled");
        }
    }

    /**
     * 记录操作开始时间
     */
    public Timer startTimer(String operationName) {
        if (!enableMonitoring) {
            return Timer.DISABLED;
        }
        return new Timer(operationName, System.currentTimeMillis());
    }

    /**
     * 记录操作完成
     */
    public void recordOperation(String operationName, long durationMs) {
        if (!enableMonitoring) {
            return;
        }

        metrics.compute(operationName, (key, existing) -> {
            if (existing == null) {
                return new OperationMetrics(operationName);
            }
            existing.record(durationMs);
            return existing;
        });

        // 定期输出性能统计（每100次操作输出一次）
        OperationMetrics operationMetrics = metrics.get(operationName);
        if (operationMetrics != null && operationMetrics.getCallCount() % 100 == 0) {
            LOGGER.info("Performance stats for {}: {}", operationName, operationMetrics.getSummary());
        }
    }

    /**
     * 获取操作指标
     */
    public OperationMetrics getMetrics(String operationName) {
        return metrics.get(operationName);
    }

    /**
     * 获取所有指标
     */
    public ConcurrentMap<String, OperationMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }

    /**
     * 清除所有指标
     */
    public void clearMetrics() {
        metrics.clear();
        LOGGER.info("Performance metrics cleared");
    }

    /**
     * 简单的计时器类
     */
    public static class Timer {
        private static final Timer DISABLED = new Timer("", -1);
        
        private final String operationName;
        private final long startTime;

        private Timer(String operationName, long startTime) {
            this.operationName = operationName;
            this.startTime = startTime;
        }

        public long stop() {
            if (startTime < 0) {
                return -1; // 禁用的计时器
            }
            long duration = System.currentTimeMillis() - startTime;
            return duration;
        }

        public String getOperationName() {
            return operationName;
        }
    }

    /**
     * 操作指标类
     */
    public static class OperationMetrics {
        private final String operationName;
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxDuration = new AtomicLong(Long.MIN_VALUE);
        private final AtomicInteger callCount = new AtomicInteger(0);

        public OperationMetrics(String operationName) {
            this.operationName = operationName;
        }

        public void record(long durationMs) {
            totalDuration.addAndGet(durationMs);
            callCount.incrementAndGet();
            
            // 更新最小值
            long currentMin = minDuration.get();
            while (durationMs < currentMin && !minDuration.compareAndSet(currentMin, durationMs)) {
                currentMin = minDuration.get();
            }
            
            // 更新最大值
            long currentMax = maxDuration.get();
            while (durationMs > currentMax && !maxDuration.compareAndSet(currentMax, durationMs)) {
                currentMax = maxDuration.get();
            }
        }

        public String getOperationName() {
            return operationName;
        }

        public long getTotalDuration() {
            return totalDuration.get();
        }

        public long getMinDuration() {
            long min = minDuration.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }

        public long getMaxDuration() {
            long max = maxDuration.get();
            return max == Long.MIN_VALUE ? 0 : max;
        }

        public int getCallCount() {
            return callCount.get();
        }

        public double getAverageDuration() {
            int count = callCount.get();
            return count > 0 ? (double) totalDuration.get() / count : 0.0;
        }

        public String getSummary() {
            return String.format("count=%d, avg=%.2fms, min=%dms, max=%dms, total=%dms",
                getCallCount(), getAverageDuration(), getMinDuration(), getMaxDuration(), getTotalDuration());
        }

        @Override
        public String toString() {
            return String.format("OperationMetrics{name='%s', %s}", operationName, getSummary());
        }
    }

    /**
     * 性能监控的装饰器，用于包装需要监控的方法
     */
    @FunctionalInterface
    public interface MonitoredOperation<T> {
        T execute() throws Exception;
    }

    /**
     * 执行被监控的操作
     */
    public <T> T executeMonitored(String operationName, MonitoredOperation<T> operation) {
        if (!enableMonitoring) {
            try {
                return operation.execute();
            } catch (Exception ex) {
                throw new RuntimeException("Operation failed: " + operationName, ex);
            }
        }

        Timer timer = startTimer(operationName);
        try {
            T result = operation.execute();
            long duration = timer.stop();
            recordOperation(operationName, duration);
            return result;
        } catch (Exception ex) {
            long duration = timer.stop();
            recordOperation(operationName + ".failed", duration);
            throw new RuntimeException("Operation failed: " + operationName, ex);
        }
    }

    /**
     * 获取性能报告
     */
    public String getPerformanceReport() {
        if (!enableMonitoring || metrics.isEmpty()) {
            return "Performance monitoring disabled or no data available";
        }

        StringBuilder report = new StringBuilder();
        report.append("=== Performance Report ===\n");
        
        for (OperationMetrics metric : metrics.values()) {
            report.append(String.format("%-30s %s\n", metric.getOperationName(), metric.getSummary()));
        }
        
        report.append("=== End of Report ===");
        return report.toString();
    }
}
