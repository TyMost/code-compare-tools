package com.example.migratediff.application.scan;

/**
 * 导出任务状态枚举
 */
public enum ExportTaskStatus {
    /**
     * 等待中
     */
    PENDING,
    
    /**
     * 处理中
     */
    PROCESSING,
    
    /**
     * 已完成
     */
    COMPLETED,
    
    /**
     * 失败
     */
    FAILED,
    
    /**
     * 已取消
     */
    CANCELLED
}
