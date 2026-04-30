package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class ScanSummaryDTO {

    private int totalFiles;
    private int oracleOnly;
    private int gaussOnly;
    private int matched;
    private double consistencyRate;
    /**
     * Overall migration coverage (Delta-O content covered by Delta-G).
     */
    private double overallCoverage;
}
