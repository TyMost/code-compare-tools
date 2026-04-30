package com.example.migratediff.api.dto;

import com.example.migratediff.domain.repo.RepoConfig;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Data
public class RepoImportRequest {

    @Valid
    @NotEmpty
    private List<RepoConfig> configs = new ArrayList<>();
}
