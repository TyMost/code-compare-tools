package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.RepoConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class IncrementalSnapshotScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalSnapshotScanner.class);

    private final SnapshotLocator snapshotLocator;
    private final SnapshotDiffExtractor snapshotDiffExtractor;
    private final IncrementalSnapshotAssembler snapshotAssembler;
    private final GitRepositoryHelper repositoryHelper;

    public IncrementalSnapshotScanner(SnapshotLocator snapshotLocator,
                                      SnapshotDiffExtractor snapshotDiffExtractor,
                                      IncrementalSnapshotAssembler snapshotAssembler,
                                      GitRepositoryHelper repositoryHelper) {
        this.snapshotLocator = snapshotLocator;
        this.snapshotDiffExtractor = snapshotDiffExtractor;
        this.snapshotAssembler = snapshotAssembler;
        this.repositoryHelper = repositoryHelper;
    }

    public DiffSummary scan(RepoConfig repoConfig) {
        DiffSummary emptySummary = buildEmptySummary(repoConfig);
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            return emptySummary;
        }
        Repository repository = null;
        try {
            repository = repositoryHelper.openRepository(repoConfig);
            if (shouldFetch(repoConfig)) {
                LOGGER.info("Snapshot scan will fetch remote {} for repo {}", resolveRemoteName(repoConfig.getRemoteName()), repoConfig.getRepoPath().getAbsolutePath());
                fetchRemote(repository, repoConfig);
            } else {
                LOGGER.debug("Snapshot scan skip fetch for repo {}", repoConfig.getRepoPath().getAbsolutePath());
            }
            SnapshotPair pair = locateSnapshots(repository, repoConfig);
            
            // 打印扫描到的提交信息
            logSnapshotDetails(repoConfig, pair);
            
            List<DiffFile> diffFiles = buildDiffFiles(repository, repoConfig, pair);
            return DiffSummary.builder()
                    .repoConfig(repoConfig)
                    .repoPath(repoConfig.getRepoPath().getAbsolutePath())
                    .baseCommitId(pair.getEarliestCommitId().name())
                    .targetCommitId(pair.getLatestCommitId().name())
                    .deltaType(repoConfig.getDeltaType())
                    .branchFrom(repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getName() : null)
                    .branchTo(repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getName() : null)
                    .scanTime(Instant.now())
                    .diffFiles(diffFiles)
                    .build();
        } catch (IOException ex) {
            LOGGER.warn("Snapshot scan failed: {}", ex.getMessage(), ex);
            return emptySummary;
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private SnapshotPair locateSnapshots(Repository repository, RepoConfig repoConfig) throws IOException {
        Instant start = resolveStartTime(repoConfig);
        Instant end = resolveEndTime(repoConfig);
        if (start == null || end == null) {
            throw new IllegalArgumentException("Snapshot scan requires timeFrom/timeTo to be set on branch config");
        }
        SnapshotLocatorOptions options = SnapshotLocatorOptions.builder()
                .includeRemoteRefs(repoConfig.isSnapshotIncludeRemoteRefs())
                .includeTags(repoConfig.isSnapshotIncludeTags())
                .maxRefs(repoConfig.getSnapshotMaxRefs())
                .build();
        return snapshotLocator.locate(repository, start, end, options);
    }

    private List<DiffFile> buildDiffFiles(Repository repository, RepoConfig repoConfig, SnapshotPair pair) throws IOException {
        List<DiffFile> files = snapshotAssembler.assemble(repository, snapshotDiffExtractor.extract(repository, pair), repoConfig);
        return files != null ? files : new ArrayList<>();
    }

    private DiffSummary buildEmptySummary(RepoConfig repoConfig) {
        return DiffSummary.builder()
                .repoConfig(repoConfig)
                .repoPath(repoConfig != null && repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : null)
                .deltaType(repoConfig != null ? repoConfig.getDeltaType() : null)
                .diffFiles(new ArrayList<>())
                .build();
    }

    private Instant resolveStartTime(RepoConfig repoConfig) {
        if (repoConfig == null) {
            return null;
        }
        if (repoConfig.getBranchFrom() != null && repoConfig.getBranchFrom().getTimeFrom() != null) {
            return repoConfig.getBranchFrom().getTimeFrom();
        }
        if (repoConfig.getBranchTo() != null) {
            return repoConfig.getBranchTo().getTimeFrom();
        }
        return null;
    }

    private Instant resolveEndTime(RepoConfig repoConfig) {
        if (repoConfig == null) {
            return null;
        }
        if (repoConfig.getBranchTo() != null && repoConfig.getBranchTo().getTimeTo() != null) {
            return repoConfig.getBranchTo().getTimeTo();
        }
        if (repoConfig.getBranchFrom() != null) {
            return repoConfig.getBranchFrom().getTimeTo();
        }
        return null;
    }

    private boolean shouldFetch(RepoConfig repoConfig) {
        return repoConfig != null && repoConfig.isFetchIfMissing();
    }

    private void fetchRemote(Repository repository, RepoConfig repoConfig) {
        if (repository == null || repoConfig == null) {
            return;
        }
        String remote = resolveRemoteName(repoConfig.getRemoteName());
        try (Git git = new Git(repository)) {
            if (!hasRemoteConfigured(git, remote)) {
                LOGGER.debug("Skip snapshot fetch because remote {} is not configured", remote);
                return;
            }
            git.fetch()
                    .setRemote(remote)
                    .setCheckFetchedObjects(true)
                    .call();
            LOGGER.info("Snapshot fetch succeeded for remote {} in repo {}", remote, repository.getDirectory());
        } catch (GitAPIException ex) {
            LOGGER.warn("Fetching remote {} before snapshot scan failed: {}", remote, ex.getMessage(), ex);
        }
    }

    private String resolveRemoteName(String remoteName) {
        return remoteName != null && !remoteName.trim().isEmpty() ? remoteName : "origin";
    }

    private boolean hasRemoteConfigured(Git git, String remoteName) {
        if (git == null || remoteName == null) {
            return false;
        }
        return git.getRepository()
                .getConfig()
                .getSubsections("remote")
                .contains(remoteName);
    }

    /**
     * 打印快照扫描结果的详细信息，包括比较的两个提交的hash值
     */
    private void logSnapshotDetails(RepoConfig repoConfig, SnapshotPair pair) {
        if (repoConfig == null || pair == null) {
            return;
        }

        String repoPath = repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : "unknown";
        Instant startTime = resolveStartTime(repoConfig);
        Instant endTime = resolveEndTime(repoConfig);
        
        LOGGER.info("=== 快照扫描结果详情 ===");
        LOGGER.info("仓库路径: {}", repoPath);
        LOGGER.info("时间范围: {} 至 {}", 
            startTime != null ? startTime.toString() : "未设置",
            endTime != null ? endTime.toString() : "未设置");
        
        if (pair.getEarliestCommitId() != null) {
            LOGGER.info("最早提交 (基准): {}", pair.getEarliestCommitId().name());
            LOGGER.info("最早提交时间: {}", pair.getEarliestInstant() != null ? pair.getEarliestInstant().toString() : "未知");
        }
        
        if (pair.getLatestCommitId() != null) {
            LOGGER.info("最晚提交 (目标): {}", pair.getLatestCommitId().name());
            LOGGER.info("最晚提交时间: {}", pair.getLatestInstant() != null ? pair.getLatestInstant().toString() : "未知");
        }
        
        LOGGER.info("增量类型: {}", repoConfig.getDeltaType() != null ? repoConfig.getDeltaType().toString() : "未设置");
        LOGGER.info("========================");
    }
}
