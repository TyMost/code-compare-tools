package com.example.migratediff.application.scan;

import com.example.migratediff.domain.migration.MigrationResult;
import lombok.Value;

@Value
public class MigrationOperationResult {
    String taskId;
    String filePath;
    MigrationResult result;
}
