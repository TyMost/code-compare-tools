package com.example.migratediff.api.dto;

import com.example.migratediff.domain.coverage.CoverageSummary;
import lombok.Data;

@Data
public class CoverageResponseDTO {

    private String taskId;
    private CoverageSummary summary;
    private String message;
}
