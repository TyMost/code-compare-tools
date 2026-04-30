package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.RepoImportRequest;
import com.example.migratediff.application.RepoAppService;
import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/import")
    public List<RepoConfig> importConfigs(@Valid @RequestBody RepoImportRequest request) {
        return repoAppService.importConfigs(request.getConfigs());
    }

    @DeleteMapping("/{id}")
    public void deleteConfig(@PathVariable("id") String id) {
        repoAppService.deleteConfig(id);
    }

    @PostMapping("/branches")
    public List<RepoBranch> fetchBranches(@Valid @RequestBody RepoConfig repoConfig) {
        return repoAppService.listBranches(repoConfig);
    }
}
