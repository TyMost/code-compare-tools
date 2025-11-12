package com.example.migratediff.application.scan;

import com.example.migratediff.domain.diff.DiffSummary;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScanInput {
    String taskId;
    boolean persistResult;
    DiffSummary oracleSummary;
    DiffSummary gaussSummary;
    ScanMode mode;
}
