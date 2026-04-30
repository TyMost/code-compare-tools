package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.ScanResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cached scan response persisted to local storage so that the UI can render
 * results without triggering a fresh Git diff on first load.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanSnapshot {
    private String repoId;
    private String repoName;
    private String taskId;
    private ScanMode mode;
    private boolean persisted;
    private Instant cachedAt;
    private ScanResponseDTO response;
}
