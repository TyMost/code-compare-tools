package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.MultiRepoExportRequestDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 导出任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTask {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 任务状态
     */
    private ExportTaskStatus status;
    
    /**
     * 导出请求
     */
    private MultiRepoExportRequestDTO request;
    
    /**
     * 创建时间
     */
    private Instant createdAt;
    
    /**
     * 开始处理时间
     */
    private Instant startedAt;
    
    /**
     * 完成时间
     */
    private Instant completedAt;
    
    /**
     * 当前处理阶段
     */
    private String currentStage;
    
    /**
     * 处理进度消息
     */
    private String progressMessage;
    
    /**
     * 当前处理数量
     */
    private int currentProgress;
    
    /**
     * 总数量
     */
    private int totalProgress;
    
    /**
     * 进度百分比
     */
    private int progressPercentage;
    
    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
    
    /**
     * 生成的文件路径
     */
    private String filePath;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private long fileSize;
    
    /**
     * 更新进度
     */
    public void updateProgress(String stage, String message, int current, int total) {
        this.currentStage = stage;
        this.progressMessage = message;
        this.currentProgress = current;
        this.totalProgress = total;
        this.progressPercentage = total > 0 ? (int) ((double) current / total * 100) : 0;
    }
    
    /**
     * 更新进度（带百分比）
     */
    public void updateProgress(String stage, String message, int percentage) {
        this.currentStage = stage;
        this.progressMessage = message;
        this.progressPercentage = Math.max(0, Math.min(100, percentage));
        this.currentProgress = percentage;
        this.totalProgress = 100;
    }
    
    /**
     * 标记为开始处理
     */
    public void markAsStarted() {
        this.status = ExportTaskStatus.PROCESSING;
        this.startedAt = Instant.now();
    }
    
    /**
     * 标记为完成
     */
    public void markAsCompleted(String filePath, String fileName, long fileSize) {
        this.status = ExportTaskStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.progressPercentage = 100;
        this.currentProgress = this.totalProgress;
    }
    
    /**
     * 标记为失败
     */
    public void markAsFailed(String errorMessage) {
        this.status = ExportTaskStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
    
    /**
     * 标记为取消
     */
    public void markAsCancelled() {
        this.status = ExportTaskStatus.CANCELLED;
        this.completedAt = Instant.now();
    }
    
    /**
     * 获取处理时长（毫秒）
     */
    public long getProcessingDurationMs() {
        if (startedAt == null) return 0;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.toEpochMilli() - startedAt.toEpochMilli();
    }
    
    /**
     * 是否正在处理中
     */
    public boolean isProcessing() {
        return status == ExportTaskStatus.PROCESSING;
    }
    
    /**
     * 是否已完成（成功或失败）
     */
    public boolean isFinished() {
        return status == ExportTaskStatus.COMPLETED || 
               status == ExportTaskStatus.FAILED || 
               status == ExportTaskStatus.CANCELLED;
    }
    
    /**
     * 是否可以下载
     */
    public boolean isDownloadable() {
        return status == ExportTaskStatus.COMPLETED && filePath != null;
    }
}
