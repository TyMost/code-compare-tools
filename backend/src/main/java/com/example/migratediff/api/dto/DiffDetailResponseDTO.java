package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class DiffDetailResponseDTO {

    private String taskId;
    private String filePath;
    private DiffContentDTO oracleDiff;
    private DiffContentDTO gaussDiff;
    private String migrationDiff;
    private DiffStatsDTO stats;
    private double coverage;
}
