package com.example.migratediff.api.mapper;

import com.example.migratediff.api.dto.MigrationApplyResponseDTO;
import com.example.migratediff.api.dto.MigrationGenerateResponseDTO;
import com.example.migratediff.domain.migration.MigrationResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MigrationMapper {

    public MigrationGenerateResponseDTO toGenerateResponse(MigrationResult result, String taskId, String filePath) {
        MigrationGenerateResponseDTO responseDTO = new MigrationGenerateResponseDTO();
        responseDTO.setTaskId(taskId);
        responseDTO.setFilePath(filePath);
        responseDTO.setMigrationDiff(result != null ? safe(result.getPreviewContent()) : "");
        return responseDTO;
    }

    public MigrationApplyResponseDTO toApplyResponse(MigrationResult result, String taskId, String filePath) {
        MigrationApplyResponseDTO responseDTO = new MigrationApplyResponseDTO();
        responseDTO.setTaskId(taskId);
        responseDTO.setFilePath(filePath);
        responseDTO.setLogId(result != null ? result.getLogId() : null);
        return responseDTO;
    }

    private String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace("\uFEFF", "")
                .replace(String.valueOf('\u00EF'), "")
                .replace(String.valueOf('\u00BB'), "")
                .replace(String.valueOf('\u00BF'), "");
    }
}
