package com.example.migratediff.api.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class ScanCacheEntryDTO {

    private String repoId;
    private String repoName;
    private String taskId;
    private String mode;
    private boolean persisted;
    private Instant cachedAt;
    private ScanResponseDTO response;
}
