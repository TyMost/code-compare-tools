package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class DiffMatrixItemDTO {

    private String filePath;
    private String oracleDelta;
    private String gaussDelta;
    private double coverage;
    /**
     * matched | oracle-only | gauss-only | partial | pending
     */
    private String status;
}
