package com.example.migratediff.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoProfileBundleDTO {
    private String version;
    private Instant generatedAt;
    @Builder.Default
    @Valid
    private List<RepoProfileDTO> profiles = new ArrayList<>();
}
