package com.example.migratediff.domain.migration;

import com.example.migratediff.domain.diff.DiffSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationSummary {

    private DiffSummary deltaOSummary;
    private DiffSummary deltaGSummary;
    private MigrationTask task;
    private MigrationResult result;
    @Builder.Default
    private List<MigrationMapping> mappings = new ArrayList<>();
}
