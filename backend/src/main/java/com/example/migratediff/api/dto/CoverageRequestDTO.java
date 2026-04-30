package com.example.migratediff.api.dto;

import com.example.migratediff.domain.diff.DeltaGroup;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CoverageRequestDTO {

    @NotBlank
    private String taskId;

    @NotNull
    private DeltaGroup deltaGroup;

    /**
     * Whether the computed result should be persisted for later retrieval.
     */
    private boolean persistResult = true;
}
