package com.example.migratediff.infrastructure.git.strategy;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Release分支分析器，用于智能选择最晚创建的Release分支
 */
@Slf4j
@Component
public class ReleaseBranchAnalyzer {

    private final CommitCache commitCache;
    private final OptimizedBranchFilter optimizedBranchFilter;

    // 缓存分支创建时间
    private final Map<String, Instant> branchCreationCache = new ConcurrentHashMap<>();
    private static final long BRANCH_CREATION_CACHE_EXPIRY_MS = 60 * 60 * 1000; // 1小时

    public ReleaseBranchAnalyzer(CommitCache commitCache, OptimizedBranchFilter optimizedBranchFilter) {
        this.commitCache = commitCache;
        this.optimizedBranchFilter = optimizedBranchFilter;
    }

    /**
     * 选择最晚创建的Release分支，并返回其在时间窗口内的最晚提交
     */
    public CommitSelectionResult selectLatestCreatedReleaseBranch(Repository repository, String releasePattern,
                                                               Instant startTime, Instant endTime) throws IOException {
        long analysisStartTime = System.currentTimeMillis();
        
        try {
            // 1. 获取所有Release分支
            List<Ref> releaseBranches = optimizedBranchFilter.filterReleaseBranches(repository, releasePattern);
            if (releaseBranches.isEmpty()) {
                log.debug("No release branches found matching pattern: {}", releasePattern);
                return CommitSelectionResult.empty();
            }

            log.debug("Found {} release branches to analyze", releaseBranches.size());

            // 2. 并行分析每个分支
            List<BranchWithCommits> branchAnalysisList = releaseBranches.parallelStream()
                    .map(branchRef -> analyzeBranch(repository, branchRef, startTime, endTime))
                    .filter(branch -> branch.isHasCommitsInWindow())
                    .collect(Collectors.toList());

            if (branchAnalysisList.isEmpty()) {
                log.debug("No release branches have commits in time window");
                return CommitSelectionResult.empty();
            }

            // 3. 直接按分支名降序排序，选择最新的release分支
            // 适用于 release_YYYYMMDD_XXX 格式，避免创建时间获取的性能和准确性问题
            log.debug("Sorting {} release branches by name (descending)", branchAnalysisList.size());
            branchAnalysisList.forEach(branch -> 
                log.debug("  Branch: {} (commits: {}, latest commit: {})", 
                         branch.getBranchName(), 
                         branch.getCommitCountInWindow(),
                         branch.getLatestCommitHash()));
            
            BranchWithCommits selectedBranch = branchAnalysisList.stream()
                    .max(Comparator.comparing(BranchWithCommits::getBranchName))
                    .orElse(null);

            if (selectedBranch == null || !selectedBranch.isValid()) {
                log.warn("Failed to select valid release branch");
                return CommitSelectionResult.empty();
            }

            long analysisEndTime = System.currentTimeMillis();
            log.info("Release branch analysis completed: selected branch '{}' (created: {}), commit: {}, analysis time: {}ms",
                    selectedBranch.getBranchName(),
                    selectedBranch.getBranchCreationTime(),
                    selectedBranch.getLatestCommitHash(),
                    analysisEndTime - analysisStartTime);

            return CommitSelectionResult.of(
                    selectedBranch.getLatestCommitInWindow(),
                    selectedBranch.getBranchName()
            );

        } catch (Exception ex) {
            log.error("Release branch analysis failed: {}", ex.getMessage(), ex);
            return CommitSelectionResult.empty();
        }
    }

    /**
     * 分析单个分支在时间窗口内的提交情况
     * 简化版本：不再依赖分支创建时间，直接使用固定值
     */
    private BranchWithCommits analyzeBranch(Repository repository, Ref branchRef, Instant startTime, Instant endTime) {
        long startTimeMs = System.currentTimeMillis();
        String branchName = extractBranchName(branchRef);
        
        try {
            // 1. 使用固定的创建时间，不再依赖Git历史遍历
            // 对于 release_YYYYMMDD_XXX 格式，分支名本身就包含了时间信息
            Instant branchCreationTime = extractDateFromBranchName(branchName);
            if (branchCreationTime == null) {
                // 如果无法从分支名解析时间，使用当前时间作为默认值
                branchCreationTime = Instant.now();
                log.debug("Could not extract date from branch name {}, using current time", branchName);
            }

            // 2. 检查缓存
            String repoPath = repository.getDirectory() != null ? repository.getDirectory().getAbsolutePath() : "unknown";
            ObjectId cachedLatest = commitCache.getCachedBranchSearch(branchName, repoPath, startTime, endTime, false);
            ObjectId cachedEarliest = commitCache.getCachedBranchSearch(branchName, repoPath, startTime, endTime, true);

            ObjectId latestCommitInWindow = cachedLatest;
            ObjectId earliestCommitInWindow = cachedEarliest;
            int commitCount = 0;

            if (latestCommitInWindow == null || earliestCommitInWindow == null) {
                // 3. 缓存未命中，需要重新搜索
                CommitSearchResult searchResult = searchCommitsInRange(repository, branchName, startTime, endTime);
                latestCommitInWindow = searchResult.getLatestCommit();
                earliestCommitInWindow = searchResult.getEarliestCommit();
                commitCount = searchResult.getCommitCount();

                // 4. 缓存结果
                if (latestCommitInWindow != null) {
                    commitCache.cacheBranchSearch(branchName, repoPath, startTime, endTime, latestCommitInWindow, false);
                }
                if (earliestCommitInWindow != null) {
                    commitCache.cacheBranchSearch(branchName, repoPath, startTime, endTime, earliestCommitInWindow, true);
                }
            }

            long endTimeMs = System.currentTimeMillis();
            
            if (latestCommitInWindow == null) {
                log.debug("No commits found in time window for branch: {}", branchName);
                return BranchWithCommits.empty(branchName);
            }

            return BranchWithCommits.of(
                    branchName,
                    branchCreationTime,
                    latestCommitInWindow,
                    earliestCommitInWindow,
                    commitCount,
                    endTimeMs - startTimeMs
            );

        } catch (Exception ex) {
            log.warn("Failed to analyze branch {}: {}", branchName, ex.getMessage());
            return BranchWithCommits.empty(branchName);
        }
    }

    /**
     * 获取分支创建时间（分支的第一个提交时间）
     */
    private Instant getBranchCreationTime(Repository repository, String branchName) {
        // 检查缓存
        Instant cached = branchCreationCache.get(branchName);
        if (cached != null && !isCacheExpired(cached)) {
            return cached;
        }

        Git git = null;
        RevWalk revWalk = null;
        try {
            git = new Git(repository);
            
            // 查找分支引用
            Ref branchRef = git.getRepository().findRef("refs/heads/" + branchName);
            if (branchRef == null) {
                branchRef = git.getRepository().findRef("refs/remotes/origin/" + branchName);
            }
            if (branchRef == null) {
                return null;
            }

            ObjectId branchId = branchRef.getObjectId();
            if (branchId == null) {
                return null;
            }

            revWalk = new RevWalk(repository);
            RevCommit commit = revWalk.parseCommit(branchId);

            // 遍历找到最早的提交（分支创建提交）
            revWalk.reset();
            revWalk.markStart(commit);
            
            RevCommit earliestCommit = null;
            for (RevCommit current : revWalk) {
                earliestCommit = current;
                // 继续遍历直到找到最早的提交
            }

            if (earliestCommit != null) {
                Instant creationTime = Instant.ofEpochSecond(earliestCommit.getCommitTime());
                // 缓存结果
                branchCreationCache.put(branchName, creationTime);
                return creationTime;
            }

        } catch (Exception ex) {
            log.warn("Failed to get creation time for branch {}: {}", branchName, ex.getMessage());
        } finally {
            if (git != null) {
                git.close();
            }
            if (revWalk != null) {
                revWalk.close();
            }
        }

        return null;
    }

    /**
     * 在时间窗口内搜索提交
     */
    private CommitSearchResult searchCommitsInRange(Repository repository, String branchName, 
                                                   Instant startTime, Instant endTime) {
        Git git = null;
        RevWalk revWalk = null;
        
        try {
            git = new Git(repository);
            
            // 查找分支引用
            Ref branchRef = git.getRepository().findRef("refs/heads/" + branchName);
            if (branchRef == null) {
                branchRef = git.getRepository().findRef("refs/remotes/origin/" + branchName);
            }
            if (branchRef == null) {
                return CommitSearchResult.empty();
            }

            ObjectId branchId = branchRef.getObjectId();
            if (branchId == null) {
                return CommitSearchResult.empty();
            }

            ObjectId latestCommit = null;
            ObjectId earliestCommit = null;
            int commitCount = 0;

            revWalk = new RevWalk(repository);
            RevCommit commit = revWalk.parseCommit(branchId);

            // 遍历提交历史
            revWalk.reset();
            revWalk.markStart(commit);
            
            for (RevCommit current : revWalk) {
                commitCount++;
                Instant commitTime = Instant.ofEpochSecond(current.getCommitTime());
                
                // 检查是否在时间窗口内
                if (!commitTime.isBefore(startTime) && !commitTime.isAfter(endTime)) {
                    if (latestCommit == null) {
                        latestCommit = current.getId();
                        earliestCommit = current.getId();
                    } else {
                        // 继续更新最早提交（因为是从新到旧遍历）
                        earliestCommit = current.getId();
                    }
                } else if (commitTime.isBefore(startTime)) {
                    // 超出时间窗口，停止遍历
                    break;
                }
            }

            return CommitSearchResult.of(latestCommit, earliestCommit, commitCount);

        } catch (Exception ex) {
            log.warn("Search commits in range failed for branch {}: {}", branchName, ex.getMessage());
            return CommitSearchResult.empty();
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
     * 从Ref中提取分支名称
     */
    private String extractBranchName(Ref ref) {
        String fullName = ref.getName();
        if (fullName.startsWith("refs/heads/")) {
            return fullName.substring("refs/heads/".length());
        } else if (fullName.startsWith("refs/remotes/origin/")) {
            return fullName.substring("refs/remotes/origin/".length());
        }
        return fullName;
    }

    /**
     * 检查缓存是否过期
     */
    private boolean isCacheExpired(Instant cachedTime) {
        return System.currentTimeMillis() - cachedTime.toEpochMilli() > BRANCH_CREATION_CACHE_EXPIRY_MS;
    }

    /**
     * 从分支名中提取日期（适用于 release_YYYYMMDD_XXX 格式）
     */
    private Instant extractDateFromBranchName(String branchName) {
        // 匹配 release_YYYYMMDD_XXX 格式
        Pattern pattern = Pattern.compile("release_(\\d{8})");
        Matcher matcher = pattern.matcher(branchName);
        
        if (matcher.find()) {
            String dateStr = matcher.group(1);
            try {
                // 解析日期 YYYYMMDD
                int year = Integer.parseInt(dateStr.substring(0, 4));
                int month = Integer.parseInt(dateStr.substring(4, 6));
                int day = Integer.parseInt(dateStr.substring(6, 8));
                
                // 转换为 Instant (假设为当天的开始时间)
                return Instant.ofEpochSecond(java.time.LocalDate.of(year, month, day).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond());
            } catch (Exception e) {
                log.debug("Failed to parse date from branch name {}: {}", branchName, e.getMessage());
                return null;
            }
        }
        
        log.debug("Could not extract date from branch name: {}", branchName);
        return null;
    }

    /**
     * 清理过期的缓存
     */
    public void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        branchCreationCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().toEpochMilli() > BRANCH_CREATION_CACHE_EXPIRY_MS);
    }

    /**
     * 提交搜索结果
     */
    private static class CommitSearchResult {
        private final ObjectId latestCommit;
        private final ObjectId earliestCommit;
        private final int commitCount;

        private CommitSearchResult(ObjectId latestCommit, ObjectId earliestCommit, int commitCount) {
            this.latestCommit = latestCommit;
            this.earliestCommit = earliestCommit;
            this.commitCount = commitCount;
        }

        public static CommitSearchResult of(ObjectId latestCommit, ObjectId earliestCommit, int commitCount) {
            return new CommitSearchResult(latestCommit, earliestCommit, commitCount);
        }

        public static CommitSearchResult empty() {
            return new CommitSearchResult(null, null, 0);
        }

        public ObjectId getLatestCommit() { return latestCommit; }
        public ObjectId getEarliestCommit() { return earliestCommit; }
        public int getCommitCount() { return commitCount; }
    }
}
