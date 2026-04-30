package com.example.migratediff.domain.migration;

import com.example.migratediff.domain.diff.DeltaGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationTask {

    private String id;
    private String taskName;
    private LocalDateTime createdAt;
    private MigrationStatus status;
    private DeltaGroup deltaGroup;
}
