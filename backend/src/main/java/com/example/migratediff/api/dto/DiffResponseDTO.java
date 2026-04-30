package com.example.migratediff.api.dto;

import com.example.migratediff.domain.diff.DiffSummary;
import lombok.Data;

@Data
public class DiffResponseDTO {

    private DiffSummary summary;
    private String message;
}
