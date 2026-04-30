package com.example.migratediff.api.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 最近扫描任务的概要信息，供前端多选导出功能展示。
 */
@Data
public class ScanTaskSummaryDTO {

    private String taskId;
    private String presetName;
    private String repoId;
    private String repoName;
    private String mode;
    private Instant generatedAt;
    private int totalFiles;
    private double overallCoverage;
}
