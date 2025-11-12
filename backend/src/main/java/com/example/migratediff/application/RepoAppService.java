package com.example.migratediff.application;

import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.infrastructure.git.GitBranchFetcher;
import com.example.migratediff.infrastructure.git.GitRepoScanner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RepoAppService {

    private final GitRepoScanner gitRepoScanner;
    private final GitBranchFetcher gitBranchFetcher;

    public RepoAppService(GitRepoScanner gitRepoScanner, GitBranchFetcher gitBranchFetcher) {
        this.gitRepoScanner = gitRepoScanner;
        this.gitBranchFetcher = gitBranchFetcher;
    }

    public List<RepoConfig> listConfigs() {
        return gitRepoScanner.scanAvailableRepos();
    }

    public List<RepoBranch> listBranches(RepoConfig config) {
        return gitBranchFetcher.fetchBranches(config);
    }

    public RepoConfig saveConfig(RepoConfig config) {
        // TODO persist configuration
        return config;
    }
}
