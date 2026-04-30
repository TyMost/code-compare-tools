package com.example.migratediff.domain.coverage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageMetric {

    private String filePath;
    private double similarityScore;
    private boolean fullyCovered;
}
