package com.example.migratediff.api.dto;

import lombok.Data;

import javax.validation.Valid;

@Data
public class ScanRequestDTO {

    /**
     * Optional task identifier allowing the backend to cache scan results.
     */
    private String taskId;

    /**
     * Persist scan/coverage results for reuse (e.g. detail, migration).
     */
    private boolean persistResult;

    /**
     * Optional preset name; when provided, the backend will populate missing repo settings.
     */
    private String presetName;

    @Valid
    private DiffRequestDTO oracle;

    @Valid
    private DiffRequestDTO gauss;
}
