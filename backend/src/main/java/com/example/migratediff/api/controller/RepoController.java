package com.example.migratediff.api.controller;

import com.example.migratediff.application.RepoAppService;
import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/repos")
public class RepoController {

    private final RepoAppService repoAppService;

    public RepoController(RepoAppService repoAppService) {
        this.repoAppService = repoAppService;
    }

    @GetMapping
    public List<RepoConfig> listConfigs() {
        return repoAppService.listConfigs();
    }

    @PostMapping
    public RepoConfig createConfig(@Valid @RequestBody RepoConfig repoConfig) {
        return repoAppService.saveConfig(repoConfig);
    }

    @PostMapping("/branches")
    public List<RepoBranch> fetchBranches(@Valid @RequestBody RepoConfig repoConfig) {
        return repoAppService.listBranches(repoConfig);
    }
}
