package com.example.migratediff.api.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MigrationGenerateRequestDTO {

    private String taskId;

    @NotBlank
    private String filePath;

    private MigrationOptionsDTO options = new MigrationOptionsDTO();

    @Data
    public static class MigrationOptionsDTO {
        private boolean ignoreWhitespace;
        private boolean ignoreComments;
    }
}
