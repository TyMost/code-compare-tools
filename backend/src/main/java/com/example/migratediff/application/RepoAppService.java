package com.example.migratediff.application;

import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.infrastructure.git.GitBranchFetcher;
import com.example.migratediff.infrastructure.persistence.RepoRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RepoAppService {

    private final GitBranchFetcher gitBranchFetcher;
    private final RepoRepository repoRepository;

    public RepoAppService(GitBranchFetcher gitBranchFetcher, RepoRepository repoRepository) {
        this.gitBranchFetcher = gitBranchFetcher;
        this.repoRepository = repoRepository;
    }

    public List<RepoConfig> listConfigs() {
        return repoRepository.findAll();
    }

    public List<RepoBranch> listBranches(RepoConfig config) {
        return gitBranchFetcher.fetchBranches(config);
    }

    public RepoConfig saveConfig(RepoConfig config) {
        if (config == null) {
            return null;
        }
        return repoRepository.save(config);
    }

    public List<RepoConfig> importConfigs(List<RepoConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }
        return configs.stream()
                .map(this::saveConfig)
                .collect(Collectors.toList());
    }

    public void deleteConfig(String id) {
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        repoRepository.deleteById(id);
    }
}
