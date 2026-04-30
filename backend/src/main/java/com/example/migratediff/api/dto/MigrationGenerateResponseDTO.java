package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class MigrationGenerateResponseDTO {

    private String taskId;
    private String filePath;
    private String migrationDiff;
}
