package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.ScanStrategy;
import com.example.migratediff.infrastructure.git.GitBranchFetcher.BranchPair;
import com.example.migratediff.infrastructure.git.strategy.TimeBasedReleaseDiffStrategy;
import com.example.migratediff.infrastructure.config.GitScanProperties;
import com.example.migratediff.infrastructure.persistence.DiffRepository;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitRepoScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoScanner.class);

    private final GitBranchFetcher gitBranchFetcher;
    private final GitDiffParser gitDiffParser;
    private final DiffRepository diffRepository;
    private final GitRepositoryHelper repositoryHelper;
    private final IncrementalSnapshotScanner incrementalSnapshotScanner;
    private final TimeBasedReleaseDiffStrategy timeBasedReleaseDiffStrategy;
    private final GitScanProperties gitScanProperties;
    private final boolean exportEnabled;

    public GitRepoScanner(GitBranchFetcher gitBranchFetcher,
                          GitDiffParser gitDiffParser,
                          @Nullable DiffRepository diffRepository,
                          GitRepositoryHelper repositoryHelper,
                          IncrementalSnapshotScanner incrementalSnapshotScanner,
                          TimeBasedReleaseDiffStrategy timeBasedReleaseDiffStrategy,
                          GitScanProperties gitScanProperties,
                          @Value("${migratediff.export.enabled:true}") boolean exportEnabled) {
        this.gitBranchFetcher = gitBranchFetcher;
        this.gitDiffParser = gitDiffParser;
        this.diffRepository = diffRepository;
        this.repositoryHelper = repositoryHelper;
        this.incrementalSnapshotScanner = incrementalSnapshotScanner;
        this.timeBasedReleaseDiffStrategy = timeBasedReleaseDiffStrategy;
        this.gitScanProperties = gitScanProperties;
        this.exportEnabled = exportEnabled;
    }

    /**
     * Incremental scan entry that assembles a {@link DiffSummary}.
     */
    public DiffSummary scan(RepoConfig repoConfig) {
        DiffSummary emptySummary = buildEmptySummary(repoConfig);
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            return emptySummary;
        }
        ScanStrategy strategy = repoConfig.getScanStrategy() != null ? repoConfig.getScanStrategy() : ScanStrategy.BRANCH;
        if (strategy == ScanStrategy.SNAPSHOT) {
            return scanSnapshot(repoConfig, emptySummary);
        }
        if (strategy == ScanStrategy.RELEASE_AUTO) {
            return scanReleaseAuto(repoConfig, emptySummary);
        }
        return scanBranch(repoConfig, emptySummary);
    }

    /**
     * Legacy entry that currently returns an empty list.
     */
    public List<RepoConfig> scanAvailableRepos() {
        return new ArrayList<>();
    }

    private DiffSummary scanSnapshot(RepoConfig repoConfig, DiffSummary emptySummary) {
        try {
            LOGGER.info("Starting snapshot scan for repo={}, timeFrom={}, timeTo={}",
                    safeRepoPath(repoConfig),
                    repoConfig != null && repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getTimeFrom() : null,
                    repoConfig != null && repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getTimeTo() : null);
            DiffSummary summary = incrementalSnapshotScanner.scan(repoConfig);
            persistSummary(summary);
            return summary;
        } catch (RuntimeException ex) {
            LOGGER.warn("Snapshot scan failed: {}", ex.getMessage(), ex);
            return emptySummary;
        }
    }

    private DiffSummary scanReleaseAuto(RepoConfig repoConfig, DiffSummary emptySummary) {
        try {
            LOGGER.info("Starting release-auto scan for repo={}, timeFrom={}, timeTo={}",
                    safeRepoPath(repoConfig),
                    repoConfig != null && repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getTimeFrom() : null,
                    repoConfig != null && repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getTimeTo() : null);
            DiffSummary summary = timeBasedReleaseDiffStrategy.scan(repoConfig);
            persistSummary(summary);
            return summary;
        } catch (RuntimeException ex) {
            LOGGER.warn("Release-auto scan failed: {}", ex.getMessage(), ex);
            return emptySummary;
        }
    }

    private DiffSummary scanBranch(RepoConfig repoConfig, DiffSummary emptySummary) {
        LOGGER.info("Starting branch scan for repo={}, branchFrom={}, branchTo={}",
                safeRepoPath(repoConfig),
                repoConfig != null && repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getName() : "null",
                repoConfig != null && repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getName() : "null");
        Repository repository = null;
        DiffFormatter formatter = null;
        try {
            repository = repositoryHelper.openRepository(repoConfig);
            BranchPair branchPair = gitBranchFetcher.resolveBranchPair(repository, repoConfig);
            if (branchPair != null) {
                LOGGER.debug("Branch scan resolved commits: base={}, target={}",
                        branchPair.getBaseId() != null ? branchPair.getBaseId().name() : "null",
                        branchPair.getTargetId() != null ? branchPair.getTargetId().name() : "null");
            }
            AbstractTreeIterator baseTree = repositoryHelper.prepareTreeIterator(repository, branchPair.getBaseId());
            AbstractTreeIterator targetTree = repositoryHelper.resolveTargetIterator(repository, branchPair.getTargetId(), repoConfig.isIncludeWorkingTree());
            formatter = repositoryHelper.createDiffFormatter(repository);
            List<DiffEntry> entries = formatter.scan(baseTree, targetTree);
            LOGGER.debug("Scanned DiffEntry count: {}", entries != null ? entries.size() : 0);
            List<DiffFile> diffFiles = gitDiffParser.parse(entries, repository, formatter, repoConfig);
            DiffSummary summary = DiffSummary.builder()
                    .repoConfig(repoConfig)
                    .repoPath(repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : null)
                    .branchFrom(repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getName() : null)
                    .branchTo(repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getName() : null)
                    .baseCommitId(branchPair.getBaseId() != null ? branchPair.getBaseId().name() : null)
                    .targetCommitId(branchPair.getTargetId() != null ? branchPair.getTargetId().name() : null)
                    .deltaType(repoConfig.getDeltaType())
                    .scanTime(Instant.now())
                    .diffFiles(diffFiles != null ? diffFiles : new ArrayList<>())
                    .build();
            persistSummary(summary);
            return summary;
        } catch (RepositoryNotFoundException ex) {
            String repoPath = repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : null;
            LOGGER.warn("Invalid repository path; missing .git directory: {}", repoPath);
            return emptySummary;
        } catch (IOException | GitAPIException ex) {
            LOGGER.warn("Failed to scan repository diffs: {}", ex.getMessage(), ex);
            return emptySummary;
        } finally {
            if (formatter != null) {
                formatter.close();
            }
            if (repository != null) {
                repository.close();
            }
        }
    }

    private DiffSummary buildEmptySummary(RepoConfig repoConfig) {
        return DiffSummary.builder()
                .repoConfig(repoConfig)
                .repoPath(repoConfig != null && repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : null)
                .branchFrom(repoConfig != null && repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getName() : null)
                .branchTo(repoConfig != null && repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getName() : null)
                .deltaType(repoConfig != null ? repoConfig.getDeltaType() : null)
                .diffFiles(new ArrayList<>())
                .build();
    }

    private void persistSummary(DiffSummary summary) {
        if (!exportEnabled || diffRepository == null || summary == null) {
            return;
        }
        try {
            diffRepository.save(summary);
            LOGGER.debug("Persisted summary for repo={}, base={}, target={}",
                    safeRepoPath(summary.getRepoConfig()),
                    summary.getBaseCommitId(),
                    summary.getTargetCommitId());
        } catch (RuntimeException ex) {
            LOGGER.warn("Persisting DiffSummary failed: {}", ex.getMessage(), ex);
        }
    }

    private String safeRepoPath(RepoConfig repoConfig) {
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            return "null";
        }
        return repoConfig.getRepoPath().getAbsolutePath();
    }
}
