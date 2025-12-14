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
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于时间窗口的自动 Release 分支差异扫描策略
 * 
 * 修复后的策略逻辑：
 * 1. Baseline 选择：master 窗口内最早 → master 窗口前最后 → 空结果
 * 2. EndCommit 选择：最晚创建的release分支在窗口内最晚提交 → master 窗口内最晚 → 空结果
 * 3. 完全独立，不依赖 legacy snapshot 逻辑
 */
@Slf4j
@Component
public class TimeBasedReleaseDiffStrategy {

    private final GitRepositoryHelper gitRepositoryHelper;
    private final GitDiffParser gitDiffParser;
    private final GitScanProperties gitScanProperties;
    private final ParallelCommitSearcher parallelCommitSearcher;
    private final CommitCache commitCache;
    private final OptimizedBranchFilter optimizedBranchFilter;
    private final ReleaseBranchAnalyzer releaseBranchAnalyzer;

    public TimeBasedReleaseDiffStrategy(GitRepositoryHelper gitRepositoryHelper,
                                        GitDiffParser gitDiffParser,
                                        GitScanProperties gitScanProperties,
                                        ParallelCommitSearcher parallelCommitSearcher,
                                        CommitCache commitCache,
                                        OptimizedBranchFilter optimizedBranchFilter,
                                        ReleaseBranchAnalyzer releaseBranchAnalyzer) {
        this.gitRepositoryHelper = gitRepositoryHelper;
        this.gitDiffParser = gitDiffParser;
        this.gitScanProperties = gitScanProperties;
        this.parallelCommitSearcher = parallelCommitSearcher;
        this.commitCache = commitCache;
        this.optimizedBranchFilter = optimizedBranchFilter;
        this.releaseBranchAnalyzer = releaseBranchAnalyzer;
    }

    /**
     * 执行 release-auto 扫描
     */
    public DiffSummary scan(RepoConfig repoConfig) {
        long startTimeMs = System.currentTimeMillis();
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

            // 打印扫描开始信息
            logScanStartInfo(repoConfig, startTime, endTime);

            // Step 1: 选择 baseline
            long baselineStartTime = System.currentTimeMillis();
            CommitSelectionResult baselineResult = selectBaselineWithBranch(repository, startTime, endTime);
            long baselineEndTime = System.currentTimeMillis();
            
            if (baselineResult == null || baselineResult.getCommit() == null) {
                log.warn("TimeBasedReleaseDiff: no valid baseline found for time window, return empty diff");
                return emptySummary;
            }

            // Step 2: 选择 endCommit
            long endCommitStartTime = System.currentTimeMillis();
            CommitSelectionResult endCommitResult = selectEndCommitWithBranch(repository, startTime, endTime);
            long endCommitEndTime = System.currentTimeMillis();
            
            if (endCommitResult == null || endCommitResult.getCommit() == null) {
                log.warn("TimeBasedReleaseDiff: no end commit found, return empty diff");
                return emptySummary;
            }

            // 打印选择的提交信息（包含分支信息）
            logSelectedCommitsWithBranches(
                baselineResult.getCommit(), baselineResult.getBranchName(),
                endCommitResult.getCommit(), endCommitResult.getBranchName(),
                baselineEndTime - baselineStartTime, endCommitEndTime - endCommitStartTime);

            // Step 3: 执行 diff
            long diffStartTime = System.currentTimeMillis();
            DiffSummary result = executeDiff(repository, repoConfig, baselineResult.getCommit(), endCommitResult.getCommit());
            long diffEndTime = System.currentTimeMillis();
            
            // 打印总体扫描结果
            logScanResult(result, diffEndTime - diffStartTime, System.currentTimeMillis() - startTimeMs);
            
            return result;

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
     * 优化版本：使用并行搜索和缓存
     */
    private ObjectId selectBaseline(Repository repository, Instant startTime, Instant endTime) throws IOException {
        CommitSelectionResult result = selectBaselineWithBranch(repository, startTime, endTime);
        return result != null ? result.getCommit() : null;
    }

    /**
     * Step 1: 选择 baseline（diff 起点）- 带分支信息版本
     * 优先级：主分支窗口内最早 → 主分支窗口前最后 → null
     * 优化版本：使用并行搜索和缓存
     */
    private CommitSelectionResult selectBaselineWithBranch(Repository repository, Instant startTime, Instant endTime) throws IOException {
        List<String> mainBranches = gitScanProperties.getMainBranches();
        String repoPath = repository.getDirectory() != null ? repository.getDirectory().getAbsolutePath() : "unknown";
        
        // 1. 并行搜索主分支在窗口内最早提交
        ParallelCommitSearcher.ParallelSearchResult earliestResults = 
            parallelCommitSearcher.searchCommitsInParallel(repository, mainBranches, startTime, endTime, true);
        
        ObjectId earliest = earliestResults.getFirstFoundCommit();
        String earliestBranch = getBranchForCommit(earliestResults, earliest);
        if (earliest != null) {
            log.info("TimeBasedReleaseDiff: selected baseline from main branches in window: {} (branch: {})", earliest.name(), earliestBranch);
            earliestResults.logResults("Baseline-Earliest");
            return CommitSelectionResult.of(earliest, earliestBranch);
        }

        // 2. fallback: 并行搜索主分支在窗口前最后提交
        ParallelCommitSearcher.ParallelSearchResult lastBeforeResults = 
            parallelCommitSearcher.searchLastCommitsBeforeParallel(repository, mainBranches, startTime);
        
        ObjectId lastBefore = lastBeforeResults.getFirstFoundCommit();
        String lastBeforeBranch = getBranchForCommit(lastBeforeResults, lastBefore);
        if (lastBefore != null) {
            log.info("TimeBasedReleaseDiff: selected baseline fallback from main branches before window: {} (branch: {})", lastBefore.name(), lastBeforeBranch);
            lastBeforeResults.logResults("Baseline-LastBefore");
            return CommitSelectionResult.of(lastBefore, lastBeforeBranch);
        }

        //3. 无有效 baseline
        return CommitSelectionResult.empty();
    }

    /**
     * Step 2: 选择 endCommit（diff 终点）
     * 修复后的逻辑：使用ReleaseBranchAnalyzer选择最晚创建的release分支
     * 优先级：最晚创建的release分支在窗口内最晚提交 → 主分支窗口内最晚 → null
     */
    private ObjectId selectEndCommit(Repository repository, Instant startTime, Instant endTime) throws IOException {
        CommitSelectionResult result = selectEndCommitWithBranch(repository, startTime, endTime);
        return result != null ? result.getCommit() : null;
    }

    /**
     * Step 2: 选择 endCommit（diff 终点）- 带分支信息版本
     * 修复后的逻辑：使用ReleaseBranchAnalyzer选择最晚创建的release分支
     * 优先级：最晚创建的release分支在窗口内最晚提交 → 主分支窗口内最晚 → null
     */
    private CommitSelectionResult selectEndCommitWithBranch(Repository repository, Instant startTime, Instant endTime) throws IOException {
        String releasePattern = gitScanProperties.getReleasePattern();
        
        // 1. 使用新的ReleaseBranchAnalyzer选择最晚创建的release分支
        try {
            CommitSelectionResult releaseResult = releaseBranchAnalyzer.selectLatestCreatedReleaseBranch(
                    repository, releasePattern, startTime, endTime);
            if (releaseResult != null && releaseResult.getCommit() != null) {
                log.info("TimeBasedReleaseDiff: selected endCommit from latest created release branch: {} (branch: {})", 
                        releaseResult.getCommit().name(), releaseResult.getBranchName());
                return releaseResult;
            }
        } catch (Exception ex) {
            log.warn("ReleaseBranchAnalyzer failed, falling back to main branches: {}", ex.getMessage());
        }

        // 2. fallback: 并行搜索主分支在窗口内最晚提交
        List<String> mainBranches = gitScanProperties.getMainBranches();
        ParallelCommitSearcher.ParallelSearchResult latestResults = 
            parallelCommitSearcher.searchCommitsInParallel(repository, mainBranches, startTime, endTime, false);
        
        ObjectId latest = latestResults.getFirstFoundCommit();
        String latestBranch = getBranchForCommit(latestResults, latest);
        if (latest != null) {
            log.info("TimeBasedReleaseDiff: selected endCommit fallback from main branches in window: {} (branch: {})", latest.name(), latestBranch);
            latestResults.logResults("EndCommit-MainBranches");
            return CommitSelectionResult.of(latest, latestBranch);
        }

        // 3. 无有效 endCommit
        return CommitSelectionResult.empty();
    }

    /**
     * 从并行搜索结果中获取对应的分支名称
     */
    private String getBranchForCommit(ParallelCommitSearcher.ParallelSearchResult searchResult, ObjectId commit) {
        if (commit == null || searchResult == null) {
            return null;
        }
        
        return searchResult.getResults().stream()
                .filter(result -> result.isFound() && result.getCommit().equals(commit))
                .map(ParallelCommitSearcher.BranchSearchResult::getBranchName)
                .findFirst()
                .orElse(null);
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
     * 打印扫描开始信息
     */
    private void logScanStartInfo(RepoConfig repoConfig, Instant startTime, Instant endTime) {
        String repoPath = repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : "unknown";
        log.info("=== Release-Auto 扫描开始 (修复版本) ===");
        log.info("仓库路径: {}", repoPath);
        log.info("时间范围: {} 至 {}", startTime != null ? startTime.toString() : "未设置", endTime != null ? endTime.toString() : "未设置");
        log.info("主分支配置: {}", gitScanProperties.getMainBranches());
        log.info("Release分支模式: {}", gitScanProperties.getReleasePattern());
        log.info("=========================");
    }

    /**
     * 打印带有分支信息的提交选择结果
     */
    private void logSelectedCommitsWithBranches(ObjectId baseline, String baselineBranch, 
                                           ObjectId endCommit, String endCommitBranch, 
                                           long baselineTime, long endCommitTime) {
        log.info("=== Release-Auto 提交选择详情 (修复版本) ===");
        log.info("基准提交 (baseline): {} (分支: {})", 
                baseline != null ? baseline.name() : "未找到", 
                baselineBranch != null ? baselineBranch : "未知");
        log.info("目标提交 (endCommit): {} (分支: {})", 
                endCommit != null ? endCommit.name() : "未找到", 
                endCommitBranch != null ? endCommitBranch : "未知");
        log.info("Baseline选择耗时: {} ms", baselineTime);
        log.info("EndCommit选择耗时: {} ms", endCommitTime);
        
        if (baseline != null && endCommit != null) {
            log.info("时间区间: {} 至 {}", 
                    baselineBranch != null ? baselineBranch : "未知分支",
                    endCommitBranch != null ? endCommitBranch : "未知分支");
        }
        log.info("===============================");
    }

    /**
     * 打印扫描结果
     */
    private void logScanResult(DiffSummary result, long diffTime, long totalTime) {
        log.info("=== Release-Auto 扫描完成 (修复版本) ===");
        if (result != null) {
            log.info("基准提交: {}", result.getBaseCommitId());
            log.info("目标提交: {}", result.getTargetCommitId());
            log.info("差异文件数量: {}", result.getDiffFiles() != null ? result.getDiffFiles().size() : 0);
        }
        log.info("Diff生成耗时: {} ms", diffTime);
        log.info("总扫描耗时: {} ms", totalTime);
        log.info("==========================");
    }

    /**
     * 获取性能统计信息
     */
    public String getPerformanceStats() {
        CommitCache.CacheStats cacheStats = commitCache.getStats();
        OptimizedBranchFilter.BranchFilterStats filterStats = optimizedBranchFilter.getStats();
        
        return String.format("Performance Stats - %s, %s", cacheStats.toString(), filterStats.toString());
    }

    /**
     * 清空所有缓存
     */
    public void clearAllCaches() {
        commitCache.clearAll();
        optimizedBranchFilter.clearAllCache();
        releaseBranchAnalyzer.cleanExpiredCache();
        log.info("All caches cleared for TimeBasedReleaseDiffStrategy");
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
