package com.example.migratediff.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoProfileDTO {

    private String id;
    private String name;
    private String presetName;
    private String version;
    @Valid
    private DiffRequestDTO oracle;
    @Valid
    private DiffRequestDTO gauss;
}
