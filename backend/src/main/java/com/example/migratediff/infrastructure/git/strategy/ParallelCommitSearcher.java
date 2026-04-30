package com.example.migratediff.infrastructure.git.strategy;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 并行提交搜索器，用于优化 release-auto 策略的性能
 */
@Slf4j
@Component
public class ParallelCommitSearcher {

    private final ExecutorService executorService;

    public ParallelCommitSearcher() {
        // 创建固定大小的线程池，避免过多并行任务
        this.executorService = Executors.newFixedThreadPool(
            Math.min(4, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "parallel-commit-searcher");
                t.setDaemon(true);
                return t;
            }
        );
    }

    /**
     * 并行搜索多个分支在时间窗口内的提交
     */
    public ParallelSearchResult searchCommitsInParallel(Repository repository, List<String> branchNames, 
                                                       Instant startTime, Instant endTime, boolean findEarliest) {
        List<CompletableFuture<BranchSearchResult>> futures = branchNames.stream()
                .map(branchName -> CompletableFuture.supplyAsync(() -> 
                    searchBranch(repository, branchName, startTime, endTime, findEarliest), executorService))
                .collect(Collectors.toList());

        // 等待所有搜索完成
        List<BranchSearchResult> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get(30, TimeUnit.SECONDS); // 30秒超时
                    } catch (Exception e) {
                        log.warn("Parallel search failed for branch: {}", e.getMessage());
                        return BranchSearchResult.empty();
                    }
                })
                .collect(Collectors.toList());

        return new ParallelSearchResult(results);
    }

    /**
     * 并行搜索多个分支在指定时间之前的最后提交
     */
    public ParallelSearchResult searchLastCommitsBeforeParallel(Repository repository, List<String> branchNames, 
                                                              Instant startTime) {
        List<CompletableFuture<BranchSearchResult>> futures = branchNames.stream()
                .map(branchName -> CompletableFuture.supplyAsync(() -> 
                    searchLastCommitBefore(repository, branchName, startTime), executorService))
                .collect(Collectors.toList());

        // 等待所有搜索完成
        List<BranchSearchResult> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get(30, TimeUnit.SECONDS); // 30秒超时
                    } catch (Exception e) {
                        log.warn("Parallel search failed for branch: {}", e.getMessage());
                        return BranchSearchResult.empty();
                    }
                })
                .collect(Collectors.toList());

        return new ParallelSearchResult(results);
    }

    /**
     * 搜索单个分支
     */
    private BranchSearchResult searchBranch(Repository repository, String branchName, 
                                          Instant startTime, Instant endTime, boolean findEarliest) {
        Git git = null;
        RevWalk revWalk = null;
        long startTimeMs = System.currentTimeMillis();
        
        try {
            git = new Git(repository);
            
            // 查找分支引用
            Ref branchRef = git.getRepository().findRef("refs/heads/" + branchName);
            if (branchRef == null) {
                branchRef = git.getRepository().findRef("refs/remotes/origin/" + branchName);
            }
            if (branchRef == null) {
                return BranchSearchResult.empty(branchName);
            }

            ObjectId branchId = branchRef.getObjectId();
            if (branchId == null) {
                return BranchSearchResult.empty(branchName);
            }

            ObjectId targetCommit = null;
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
                    if (findEarliest) {
                        // 找最早的：需要遍历完整个时间窗口，保留最早的提交
                        targetCommit = current.getId();
                        // 不要break，继续遍历寻找更早的提交
                    } else {
                        // 找最晚的：因为是从新到旧遍历，第一个就是最晚的
                        targetCommit = current.getId();
                        break; // 找到就停止
                    }
                } else if (commitTime.isBefore(startTime)) {
                    // 超出时间窗口，停止遍历
                    break;
                }
            }

            long endTimeMs = System.currentTimeMillis();
            return BranchSearchResult.of(branchName, targetCommit, commitCount, endTimeMs - startTimeMs);

        } catch (Exception ex) {
            log.warn("Search branch {} failed: {}", branchName, ex.getMessage());
            return BranchSearchResult.empty(branchName);
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
     * 搜索单个分支在指定时间之前的最后提交
     */
    private BranchSearchResult searchLastCommitBefore(Repository repository, String branchName, Instant startTime) {
        Git git = null;
        RevWalk revWalk = null;
        long startTimeMs = System.currentTimeMillis();
        
        try {
            git = new Git(repository);
            
            // 查找分支引用
            Ref branchRef = git.getRepository().findRef("refs/heads/" + branchName);
            if (branchRef == null) {
                branchRef = git.getRepository().findRef("refs/remotes/origin/" + branchName);
            }
            if (branchRef == null) {
                return BranchSearchResult.empty(branchName);
            }

            ObjectId branchId = branchRef.getObjectId();
            if (branchId == null) {
                return BranchSearchResult.empty(branchName);
            }

            ObjectId lastBefore = null;
            int commitCount = 0;

            revWalk = new RevWalk(repository);
            RevCommit commit = revWalk.parseCommit(branchId);

            // 遍历提交历史
            revWalk.reset();
            revWalk.markStart(commit);
            
            for (RevCommit current : revWalk) {
                commitCount++;
                Instant commitTime = Instant.ofEpochSecond(current.getCommitTime());
                
                if (commitTime.isBefore(startTime)) {
                    lastBefore = current.getId();
                    break;
                }
            }

            long endTimeMs = System.currentTimeMillis();
            return BranchSearchResult.of(branchName, lastBefore, commitCount, endTimeMs - startTimeMs);

        } catch (Exception ex) {
            log.warn("Search last commit before {} failed: {}", branchName, ex.getMessage());
            return BranchSearchResult.empty(branchName);
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
     * 关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 分支搜索结果
     */
    public static class BranchSearchResult {
        private final String branchName;
        private final ObjectId commit;
        private final int commitCount;
        private final long searchTime;
        private final boolean found;

        private BranchSearchResult(String branchName, ObjectId commit, int commitCount, long searchTime, boolean found) {
            this.branchName = branchName;
            this.commit = commit;
            this.commitCount = commitCount;
            this.searchTime = searchTime;
            this.found = found;
        }

        public static BranchSearchResult of(String branchName, ObjectId commit, int commitCount, long searchTime) {
            return new BranchSearchResult(branchName, commit, commitCount, searchTime, commit != null);
        }

        public static BranchSearchResult empty(String branchName) {
            return new BranchSearchResult(branchName, null, 0, 0, false);
        }

        public static BranchSearchResult empty() {
            return new BranchSearchResult(null, null, 0, 0, false);
        }

        public String getBranchName() { return branchName; }
        public ObjectId getCommit() { return commit; }
        public int getCommitCount() { return commitCount; }
        public long getSearchTime() { return searchTime; }
        public boolean isFound() { return found; }
    }

    /**
     * 并行搜索结果
     */
    public static class ParallelSearchResult {
        private final List<BranchSearchResult> results;
        private final long totalSearchTime;

        public ParallelSearchResult(List<BranchSearchResult> results) {
            this.results = results;
            this.totalSearchTime = results.stream().mapToLong(BranchSearchResult::getSearchTime).max().orElse(0);
        }

        public ObjectId getFirstFoundCommit() {
            return results.stream()
                    .filter(BranchSearchResult::isFound)
                    .map(BranchSearchResult::getCommit)
                    .findFirst()
                    .orElse(null);
        }

        public List<BranchSearchResult> getResults() { return results; }
        public long getTotalSearchTime() { return totalSearchTime; }

        public void logResults(String operation) {
            log.debug("=== {} 并行搜索结果 ===", operation);
            results.stream()
                    .filter(BranchSearchResult::isFound)
                    .forEach(result -> log.debug("分支 {}: 找到提交 {}, 耗时 {}ms, 检查了 {} 个提交", 
                            result.getBranchName(), 
                            result.getCommit().name(), 
                            result.getSearchTime(),
                            result.getCommitCount()));
            log.debug("总搜索时间: {}ms", totalSearchTime);
        }
    }
}
