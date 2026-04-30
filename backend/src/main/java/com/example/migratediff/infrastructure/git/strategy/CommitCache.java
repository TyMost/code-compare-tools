package com.example.migratediff.infrastructure.git.strategy;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 提交时间缓存，用于避免重复解析提交时间
 */
@Slf4j
@Component
public class CommitCache {

    private final ConcurrentHashMap<String, CachedCommit> commitCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedBranchSearch> branchSearchCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（分钟）
    private static final long COMMIT_CACHE_EXPIRY_MINUTES = 30;
    private static final long BRANCH_SEARCH_CACHE_EXPIRY_MINUTES = 15;

    /**
     * 缓存提交时间
     */
    public void cacheCommitTime(ObjectId commitId, Instant commitTime) {
        if (commitId == null || commitTime == null) {
            return;
        }
        
        String key = commitId.name();
        CachedCommit cached = new CachedCommit(commitId, commitTime, System.currentTimeMillis());
        commitCache.put(key, cached);
        
        // 清理过期缓存
        cleanExpiredCommits();
    }

    /**
     * 获取缓存的提交时间
     */
    public Instant getCachedCommitTime(ObjectId commitId) {
        if (commitId == null) {
            return null;
        }
        
        String key = commitId.name();
        CachedCommit cached = commitCache.get(key);
        
        if (cached == null) {
            return null;
        }
        
        // 检查是否过期
        long currentTime = System.currentTimeMillis();
        if (currentTime - cached.getTimestamp() > TimeUnit.MINUTES.toMillis(COMMIT_CACHE_EXPIRY_MINUTES)) {
            commitCache.remove(key);
            return null;
        }
        
        return cached.getCommitTime();
    }

    /**
     * 缓存分支搜索结果
     */
    public void cacheBranchSearch(String branchName, String repoPath, Instant startTime, Instant endTime, 
                                 ObjectId result, boolean findEarliest) {
        if (branchName == null || repoPath == null || startTime == null || endTime == null) {
            return;
        }
        
        String key = buildBranchSearchKey(branchName, repoPath, startTime, endTime, findEarliest);
        CachedBranchSearch cached = new CachedBranchSearch(result, System.currentTimeMillis());
        branchSearchCache.put(key, cached);
        
        // 清理过期缓存
        cleanExpiredBranchSearches();
    }

    /**
     * 获取缓存的分支搜索结果
     */
    public ObjectId getCachedBranchSearch(String branchName, String repoPath, Instant startTime, Instant endTime, 
                                        boolean findEarliest) {
        if (branchName == null || repoPath == null || startTime == null || endTime == null) {
            return null;
        }
        
        String key = buildBranchSearchKey(branchName, repoPath, startTime, endTime, findEarliest);
        CachedBranchSearch cached = branchSearchCache.get(key);
        
        if (cached == null) {
            return null;
        }
        
        // 检查是否过期
        long currentTime = System.currentTimeMillis();
        if (currentTime - cached.getTimestamp() > TimeUnit.MINUTES.toMillis(BRANCH_SEARCH_CACHE_EXPIRY_MINUTES)) {
            branchSearchCache.remove(key);
            return null;
        }
        
        return cached.getResult();
    }

    /**
     * 清理过期的提交缓存
     */
    private void cleanExpiredCommits() {
        long currentTime = System.currentTimeMillis();
        long expiryTime = TimeUnit.MINUTES.toMillis(COMMIT_CACHE_EXPIRY_MINUTES);
        
        commitCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getTimestamp() > expiryTime);
    }

    /**
     * 清理过期的分支搜索缓存
     */
    private void cleanExpiredBranchSearches() {
        long currentTime = System.currentTimeMillis();
        long expiryTime = TimeUnit.MINUTES.toMillis(BRANCH_SEARCH_CACHE_EXPIRY_MINUTES);
        
        branchSearchCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getTimestamp() > expiryTime);
    }

    /**
     * 构建分支搜索缓存键
     */
    private String buildBranchSearchKey(String branchName, String repoPath, Instant startTime, Instant endTime, boolean findEarliest) {
        return String.format("%s:%s:%s:%s:%s", 
                branchName, 
                repoPath.hashCode(), // 使用路径哈希避免特殊字符
                startTime.toEpochMilli(), 
                endTime.toEpochMilli(),
                findEarliest ? "E" : "L");
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        commitCache.clear();
        branchSearchCache.clear();
        log.info("Commit cache cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(commitCache.size(), branchSearchCache.size());
    }

    /**
     * 缓存的提交信息
     */
    private static class CachedCommit {
        private final ObjectId commitId;
        private final Instant commitTime;
        private final long timestamp;

        public CachedCommit(ObjectId commitId, Instant commitTime, long timestamp) {
            this.commitId = commitId;
            this.commitTime = commitTime;
            this.timestamp = timestamp;
        }

        public ObjectId getCommitId() { return commitId; }
        public Instant getCommitTime() { return commitTime; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 缓存的分支搜索结果
     */
    private static class CachedBranchSearch {
        private final ObjectId result;
        private final long timestamp;

        public CachedBranchSearch(ObjectId result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }

        public ObjectId getResult() { return result; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final int commitCacheSize;
        private final int branchSearchCacheSize;

        public CacheStats(int commitCacheSize, int branchSearchCacheSize) {
            this.commitCacheSize = commitCacheSize;
            this.branchSearchCacheSize = branchSearchCacheSize;
        }

        public int getCommitCacheSize() { return commitCacheSize; }
        public int getBranchSearchCacheSize() { return branchSearchCacheSize; }

        @Override
        public String toString() {
            return String.format("CacheStats{commits=%d, branchSearches=%d}", 
                    commitCacheSize, branchSearchCacheSize);
        }
    }
}
