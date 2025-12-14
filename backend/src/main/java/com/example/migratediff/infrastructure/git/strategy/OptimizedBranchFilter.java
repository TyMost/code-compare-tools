package com.example.migratediff.infrastructure.git.strategy;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 优化的分支过滤器，用于提升 release 分支过滤性能
 */
@Slf4j
@Component
public class OptimizedBranchFilter {

    // 缓存编译后的正则表达式
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();
    
    // 缓存分支列表（按仓库路径）
    private final Map<String, CachedBranchList> branchListCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（分钟）
    private static final long BRANCH_LIST_CACHE_EXPIRY_MINUTES = 10;

    /**
     * 过滤 release 分支（优化版本）
     */
    public List<Ref> filterReleaseBranches(Repository repository, String releasePattern) throws IOException {
        String repoPath = repository.getDirectory() != null ? repository.getDirectory().getAbsolutePath() : "unknown";
        
        // 检查缓存
        CachedBranchList cached = branchListCache.get(repoPath);
        if (cached != null && !cached.isExpired()) {
            return filterBranchesByPattern(cached.getBranches(), releasePattern);
        }
        
        // 重新获取分支列表
        List<Ref> allBranches = fetchAllBranches(repository);
        
        // 缓存结果
        branchListCache.put(repoPath, new CachedBranchList(allBranches, System.currentTimeMillis()));
        
        // 清理过期缓存
        cleanExpiredCache();
        
        return filterBranchesByPattern(allBranches, releasePattern);
    }

    /**
     * 获取所有分支（包含本地和远程）
     */
    private List<Ref> fetchAllBranches(Repository repository) throws IOException {
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
            
            log.debug("Fetched {} branches from repository", branches.size());
            return branches;
            
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    /**
     * 使用缓存的正则表达式过滤分支
     */
    private List<Ref> filterBranchesByPattern(List<Ref> branches, String releasePattern) {
        Pattern pattern = getCompiledPattern(releasePattern);
        
        return branches.stream()
                .filter(ref -> isReleaseBranch(ref, pattern))
                .collect(Collectors.toList());
    }

    /**
     * 获取编译后的正则表达式（带缓存）
     */
    private Pattern getCompiledPattern(String releasePattern) {
        return compiledPatterns.computeIfAbsent(releasePattern, pattern -> {
            String regex = pattern.replace("*", ".*");
            log.debug("Compiled pattern '{}' to regex '{}'", pattern, regex);
            return Pattern.compile(regex);
        });
    }

    /**
     * 检查是否为 release 分支
     */
    private boolean isReleaseBranch(Ref ref, Pattern pattern) {
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
    }

    /**
     * 清理过期的缓存
     */
    private void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        long expiryTime = BRANCH_LIST_CACHE_EXPIRY_MINUTES * 60 * 1000;
        
        branchListCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getTimestamp() > expiryTime);
    }

    /**
     * 预过滤可能包含时间窗口内提交的分支
     */
    public List<String> preFilterBranches(Repository repository, List<String> branchNames, 
                                        Instant startTime, Instant endTime) {
        String repoPath = repository.getDirectory() != null ? repository.getDirectory().getAbsolutePath() : "unknown";
        
        // 简单的启发式过滤：如果分支的最新提交在时间窗口之前，可以跳过
        List<String> candidateBranches = new ArrayList<>();
        
        Git git = null;
        try {
            git = new Git(repository);
            
            for (String branchName : branchNames) {
                try {
                    Ref branchRef = git.getRepository().findRef("refs/heads/" + branchName);
                    if (branchRef == null) {
                        branchRef = git.getRepository().findRef("refs/remotes/origin/" + branchName);
                    }
                    
                    if (branchRef != null && branchRef.getObjectId() != null) {
                        // 这里可以进一步优化：检查最新提交的时间
                        candidateBranches.add(branchName);
                    }
                } catch (Exception ex) {
                    log.debug("Failed to check branch {}: {}", branchName, ex.getMessage());
                }
            }
            
        } finally {
            if (git != null) {
                git.close();
            }
        }
        
        return candidateBranches;
    }

    /**
     * 清空所有缓存
     */
    public void clearAllCache() {
        branchListCache.clear();
        compiledPatterns.clear();
        log.info("Branch filter cache cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public BranchFilterStats getStats() {
        return new BranchFilterStats(branchListCache.size(), compiledPatterns.size());
    }

    /**
     * 缓存的分支列表
     */
    private static class CachedBranchList {
        private final List<Ref> branches;
        private final long timestamp;

        public CachedBranchList(List<Ref> branches, long timestamp) {
            this.branches = new ArrayList<>(branches); // 创建副本避免外部修改
            this.timestamp = timestamp;
        }

        public List<Ref> getBranches() { return branches; }
        public long getTimestamp() { return timestamp; }
        
        public boolean isExpired() {
            long currentTime = System.currentTimeMillis();
            return currentTime - timestamp > BRANCH_LIST_CACHE_EXPIRY_MINUTES * 60 * 1000;
        }
    }

    /**
     * 分支过滤器统计信息
     */
    public static class BranchFilterStats {
        private final int branchListCacheSize;
        private final int compiledPatternsSize;

        public BranchFilterStats(int branchListCacheSize, int compiledPatternsSize) {
            this.branchListCacheSize = branchListCacheSize;
            this.compiledPatternsSize = compiledPatternsSize;
        }

        public int getBranchListCacheSize() { return branchListCacheSize; }
        public int getCompiledPatternsSize() { return compiledPatternsSize; }

        @Override
        public String toString() {
            return String.format("BranchFilterStats{branchLists=%d, patterns=%d}", 
                    branchListCacheSize, compiledPatternsSize);
        }
    }
}
