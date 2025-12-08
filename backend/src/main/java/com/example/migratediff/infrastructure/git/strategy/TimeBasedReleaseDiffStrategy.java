package com.example.migratediff.infrastructure.git.strategy;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.infrastructure.git.GitDiffParser;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import com.example.migratediff.infrastructure.config.GitScanProperties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于时间窗口的自动 Release 分支差异扫描策略
 * 
 * 策略逻辑：
 * 1. Baseline 选择：master 窗口内最早 → master 窗口前最后 → 空结果
 * 2. EndCommit 选择：release 分支最新 → master 窗口内最晚 → 空结果
 * 3. 完全独立，不依赖 legacy snapshot 逻辑
 */
@Slf4j
@Component
public class TimeBasedReleaseDiffStrategy {

    private final GitRepositoryHelper gitRepositoryHelper;
    private final GitDiffParser gitDiffParser;
    private final GitScanProperties gitScanProperties;

    public TimeBasedReleaseDiffStrategy(GitRepositoryHelper gitRepositoryHelper,
                                        GitDiffParser gitDiffParser,
                                        GitScanProperties gitScanProperties) {
        this.gitRepositoryHelper = gitRepositoryHelper;
        this.gitDiffParser = gitDiffParser;
        this.gitScanProperties = gitScanProperties;
    }

    /**
     * 执行 release-auto 扫描
     */
    public DiffSummary scan(RepoConfig repoConfig) {
        DiffSummary emptySummary = buildEmptySummary(repoConfig);
        
        if (!validateConfig(repoConfig)) {
            log.warn("TimeBasedReleaseDiff: invalid config, return empty diff");
            return emptySummary;
        }

        Repository repository = null;
        try {
            repository = gitRepositoryHelper.openRepository(repoConfig);
            
            // 执行 fetch（如果需要）
            if (repoConfig.isFetchIfMissing()) {
                fetchRemote(repository, repoConfig.getRemoteName());
            }

            // 解析时间窗口
            Instant startTime = repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getTimeFrom() : null;
            Instant endTime = repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getTimeTo() : null;
            
            if (startTime == null || endTime == null) {
                log.warn("TimeBasedReleaseDiff: time window not specified, return empty diff");
                return emptySummary;
            }

            // Step 1: 选择 baseline
            ObjectId baseline = selectBaseline(repository, startTime, endTime);
            if (baseline == null) {
                log.warn("TimeBasedReleaseDiff: no valid baseline found for time window, return empty diff");
                return emptySummary;
            }

            // Step 2: 选择 endCommit
            ObjectId endCommit = selectEndCommit(repository, startTime, endTime);
            if (endCommit == null) {
                log.warn("TimeBasedReleaseDiff: no end commit found, return empty diff");
                return emptySummary;
            }

            // Step 3: 执行 diff
            return executeDiff(repository, repoConfig, baseline, endCommit);

        } catch (Exception ex) {
            log.warn("TimeBasedReleaseDiff: scan failed: {}", ex.getMessage(), ex);
            return emptySummary;
        } finally {
            if (repository != null) {
                gitRepositoryHelper.closeRepository(repository);
            }
        }
    }

    /**
     * 验证配置
     */
    private boolean validateConfig(RepoConfig repoConfig) {
        return repoConfig != null 
                && repoConfig.getRepoPath() != null
                && repoConfig.getBranchFrom() != null
                && repoConfig.getBranchFrom().getTimeFrom() != null
                && repoConfig.getBranchTo() != null
                && repoConfig.getBranchTo().getTimeTo() != null;
    }

    /**
     * Step 1: 选择 baseline（diff 起点）
     * 优先级：主分支窗口内最早 → 主分支窗口前最后 → null
     */
    private ObjectId selectBaseline(Repository repository, Instant startTime, Instant endTime) throws IOException {
        List<String> mainBranches = gitScanProperties.getMainBranches();
        
        // 1. 主分支在窗口内最早提交
        for (String branch : mainBranches) {
            ObjectId earliest = findCommitInRange(repository, branch, startTime, endTime, true);
            if (earliest != null) {
                log.info("TimeBasedReleaseDiff: selected baseline from {} in window: {}", branch, earliest.name());
                return earliest;
            }
        }

        // 2. fallback: 主分支在窗口前最后提交
        for (String branch : mainBranches) {
            ObjectId lastBefore = findLastCommitBefore(repository, branch, startTime);
            if (lastBefore != null) {
                log.info("TimeBasedReleaseDiff: selected baseline fallback from {} before window: {}", branch, lastBefore.name());
                return lastBefore;
            }
        }

        // 3. 无有效 baseline
        return null;
    }

    /**
     * Step 2: 选择 endCommit（diff 终点）
     * 优先级：release 分支最新 → 主分支窗口内最晚 → null
     */
    private ObjectId selectEndCommit(Repository repository, Instant startTime, Instant endTime) throws IOException {
        String releasePattern = gitScanProperties.getReleasePattern();
        
        // 1. 找 release 分支在窗口内最晚提交
        ObjectId latestRelease = findLatestReleaseCommitInWindow(repository, releasePattern, startTime, endTime);
        if (latestRelease != null) {
            log.info("TimeBasedReleaseDiff: selected endCommit from release branch: {}", latestRelease.name());
            return latestRelease;
        }

        // 2. fallback: 主分支在窗口内最晚提交
        List<String> mainBranches = gitScanProperties.getMainBranches();
        for (String branch : mainBranches) {
            ObjectId latest = findCommitInRange(repository, branch, startTime, endTime, false);
            if (latest != null) {
                log.info("TimeBasedReleaseDiff: selected endCommit fallback from {} in window: {}", branch, latest.name());
                return latest;
            }
        }

        // 3. 无有效 endCommit
        return null;
    }

    /**
     * 在时间窗口内查找指定分支的提交
     * @param earliest 为 true 返回最早，false 返回最晚
     */
    private ObjectId findCommitInRange(Repository repository, String branchName, 
                                     Instant startTime, Instant endTime, boolean earliest) throws IOException {
        Git git = null;
        RevWalk revWalk = null;
        try {
            git = new Git(repository);
            
            // 查找分支引用
            Ref branchRef = git.getRepository().findRef("refs/heads/" + branchName);
            if (branchRef == null) {
                // 尝试远程分支
                branchRef = git.getRepository().findRef("refs/remotes/origin/" + branchName);
            }
            if (branchRef == null) {
                return null;
            }

            ObjectId branchId = branchRef.getObjectId();
            if (branchId == null) {
                return null;
            }

            ObjectId targetCommit = null;

            revWalk = new RevWalk(repository);
            RevCommit commit = revWalk.parseCommit(branchId);

            // 遍历提交历史
            revWalk.reset();
            revWalk.markStart(commit);
            
            for (RevCommit current : revWalk) {
                Instant commitTime = Instant.ofEpochSecond(current.getCommitTime());
                
                // 检查是否在时间窗口内
                if (!commitTime.isBefore(startTime) && !commitTime.isAfter(endTime)) {
                    if (targetCommit == null) {
                        targetCommit = current.getId();
                    }
                    
                    if (earliest) {
                        // 找最早的，一旦找到就停止
                        break;
                    } else {
                        // 找最晚的，继续遍历
                        targetCommit = current.getId();
                    }
                } else if (commitTime.isBefore(startTime)) {
                    // 超出时间窗口，停止遍历
                    break;
                }
            }

            return targetCommit;
        } finally {
            if (git != null) {
                git.close();
            }
            if (revWalk != null) {
                revWalk.close();
            }
        }
    }

    /**
     * 查找指定分支在指定时间之前的最后一个提交
     */
    private ObjectId findLastCommitBefore(Repository repository, String branchName, Instant startTime) throws IOException {
        Git git = null;
            RevWalk revWalk = null;
        try {
            git = new Git(repository);
            
            // 查找分支引用
            Ref branchRef = git.getRepository().findRef("refs/heads/" + branchName);
            if (branchRef == null) {
                // 尝试远程分支
                branchRef = git.getRepository().findRef("refs/remotes/origin/" + branchName);
            }
            if (branchRef == null) {
                return null;
            }

            ObjectId branchId = branchRef.getObjectId();
            if (branchId == null) {
                return null;
            }

            ObjectId lastBefore = null;

            revWalk = new RevWalk(repository);
            RevCommit commit = revWalk.parseCommit(branchId);

            // 遍历提交历史
            revWalk.reset();
            revWalk.markStart(commit);
            
            for (RevCommit current : revWalk) {
                Instant commitTime = Instant.ofEpochSecond(current.getCommitTime());
                
                if (commitTime.isBefore(startTime)) {
                    lastBefore = current.getId();
                    break;
                }
            }

            return lastBefore;
        } finally {
            if (git != null) {
                git.close();
            }
            if (revWalk != null) {
                revWalk.close();
            }
        }
    }

    /**
     * 过滤 release 分支（包含本地和远程分支）
     */
    private List<Ref> filterReleaseBranches(Repository repository, String releasePattern) throws IOException {
        Pattern pattern = Pattern.compile(releasePattern.replace("*", ".*"));
        
        Git git = null;
        try {
            git = new Git(repository);
            List<Ref> branches;
            try {
                // 获取所有分支（包括远程分支）
                branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            } catch (GitAPIException ex) {
                log.warn("Failed to list branches: {}", ex.getMessage());
                return new ArrayList<>();
            }
            
            return branches.stream()
                    .filter(ref -> {
                        String branchName = ref.getName();
                        // 支持本地分支 refs/heads/ 和远程分支 refs/remotes/origin/
                        if (branchName.startsWith("refs/heads/")) {
                            branchName = branchName.substring("refs/heads/".length());
                            return pattern.matcher(branchName).matches();
                        } else if (branchName.startsWith("refs/remotes/origin/")) {
                            branchName = branchName.substring("refs/remotes/origin/".length());
                            return pattern.matcher(branchName).matches();
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * 选择时间窗口内创建时间最晚的 release 分支，并返回其最晚提交
     */
    private ObjectId findLatestReleaseCommitInWindow(Repository repository, String releasePattern, 
                                                    Instant startTime, Instant endTime) throws IOException {
        List<Ref> releaseBranches = filterReleaseBranches(repository, releasePattern);
        if (releaseBranches.isEmpty()) {
            log.debug("No release branches found matching pattern: {}", releasePattern);
            return null;
        }

        ObjectId latestCommit = null;
        Instant latestBranchCreation = null;

        Git git = null;
        RevWalk revWalk = null;
        try {
            git = new Git(repository);
            revWalk = new RevWalk(repository);

            for (Ref branchRef : releaseBranches) {
                try {
                    ObjectId branchId = branchRef.getObjectId();
                    if (branchId == null) {
                        continue;
                    }

                    RevCommit commit = revWalk.parseCommit(branchId);
                    
                    // 提取分支名称（支持本地和远程分支）
                    String branchName;
                    if (branchRef.getName().startsWith("refs/heads/")) {
                        branchName = branchRef.getName().substring("refs/heads/".length());
                    } else if (branchRef.getName().startsWith("refs/remotes/origin/")) {
                        branchName = branchRef.getName().substring("refs/remotes/origin/".length());
                    } else {
                        continue;
                    }
                    
                    // 找到该分支在时间窗口内的最晚提交
                    ObjectId branchLatest = findCommitInRange(
                        repository, 
                        branchName,
                        startTime, 
                        endTime, 
                        false
                    );
                    
                    if (branchLatest != null) {
                        RevCommit latestCommitObj = revWalk.parseCommit(branchLatest);
                        Instant branchCreationTime = Instant.ofEpochSecond(latestCommitObj.getCommitTime());
                        
                        if (latestBranchCreation == null || branchCreationTime.isAfter(latestBranchCreation)) {
                            latestBranchCreation = branchCreationTime;
                            latestCommit = branchLatest;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Failed to process release branch {}: {}", branchRef.getName(), ex.getMessage());
                }
            }
        } finally {
            if (git != null) {
                git.close();
            }
            if (revWalk != null) {
                revWalk.close();
            }
        }

        return latestCommit;
    }

    /**
     * Step 3: 执行 diff
     */
    private DiffSummary executeDiff(Repository repository, RepoConfig repoConfig, 
                                   ObjectId baseline, ObjectId endCommit) throws IOException {
        DiffFormatter formatter = null;
        try {
            // 复用现有的工具类
            AbstractTreeIterator baseTree = gitRepositoryHelper.prepareTreeIterator(repository, baseline);
            AbstractTreeIterator targetTree = gitRepositoryHelper.prepareTreeIterator(repository, endCommit);
            formatter = gitRepositoryHelper.createDiffFormatter(repository);

            // 复用 JGit 标准操作
            List<DiffEntry> entries = formatter.scan(baseTree, targetTree);

            // 复用现有的解析器
            List<DiffFile> diffFiles = gitDiffParser.parse(entries, repository, formatter, repoConfig);

            // 构建标准返回结果
            return DiffSummary.builder()
                    .repoConfig(repoConfig)
                    .repoPath(repoConfig.getRepoPath().getAbsolutePath())
                    .branchFrom(repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getName() : null)
                    .branchTo(repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getName() : null)
                    .baseCommitId(baseline.name())
                    .targetCommitId(endCommit.name())
                    .deltaType(repoConfig.getDeltaType())
                    .scanTime(Instant.now())
                    .diffFiles(diffFiles != null ? diffFiles : new ArrayList<>())
                    .build();

        } finally {
            if (formatter != null) {
                formatter.close();
            }
        }
    }

    /**
     * 执行远程 fetch
     */
    private void fetchRemote(Repository repository, String remoteName) {
        String remote = remoteName != null && !remoteName.trim().isEmpty() ? remoteName : "origin";
        try (Git git = new Git(repository)) {
            if (!hasRemoteConfigured(git, remote)) {
                log.debug("Skip fetch because remote {} is not configured", remote);
                return;
            }
            git.fetch()
                    .setRemote(remote)
                    .setCheckFetchedObjects(true)
                    .call();
            log.info("Fetch succeeded for remote {} in repo {}", remote, repository.getDirectory());
        } catch (GitAPIException ex) {
            log.warn("Fetching remote {} failed: {}", remote, ex.getMessage());
        }
    }

    /**
     * 检查远程是否已配置
     */
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
     * 构建空的 DiffSummary
     */
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
}
