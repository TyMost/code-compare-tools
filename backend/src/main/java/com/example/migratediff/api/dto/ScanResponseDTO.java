package com.example.migratediff.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ScanResponseDTO {

    private String taskId;
    private ScanSummaryDTO summary;
    private List<DiffMatrixItemDTO> diffMatrix = new ArrayList<>();
}
