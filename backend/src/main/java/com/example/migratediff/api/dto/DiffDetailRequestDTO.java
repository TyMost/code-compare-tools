package com.example.migratediff.api.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DiffDetailRequestDTO {

    private String taskId;

    @NotBlank
    private String filePath;
}
