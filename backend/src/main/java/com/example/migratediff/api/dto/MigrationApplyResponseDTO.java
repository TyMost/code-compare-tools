package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class MigrationApplyResponseDTO {

    private String taskId;
    private String filePath;
    private Long logId;
}
