package com.example.migratediff.api.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MigrationApplyRequestDTO {

    private String taskId;

    @NotBlank
    private String filePath;
}
