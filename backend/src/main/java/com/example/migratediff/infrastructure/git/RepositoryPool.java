package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Repository对象缓存池，用于管理和复用Repository实例，减少重复的文件I/O操作
 * 支持JDK 8兼容性实现
 */
@Component
public class RepositoryPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryPool.class);

    private final Map<String, RepositoryWrapper> repositoryCache = new ConcurrentHashMap<>();
    private final ReentrantLock cleanupLock = new ReentrantLock();
    private final ScheduledExecutorService cleanupExecutor;
    private final int maxPoolSize;
    private final long maxIdleTime;
    private final boolean enablePool;

    public RepositoryPool(@Value("${migratediff.performance.repository-pool-size:10}") int maxPoolSize,
                         @Value("${migratediff.performance.cache-repository-seconds:300}") long maxIdleTime,
                         @Value("${migratediff.performance.enable-repository-pool:true}") boolean enablePool) {
        this.maxPoolSize = maxPoolSize;
        this.maxIdleTime = maxIdleTime * 1000; // 转换为毫秒
        this.enablePool = enablePool;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "repository-pool-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动定期清理任务
        if (enablePool) {
            cleanupExecutor.scheduleWithFixedDelay(this::cleanupIdleRepositories, 
                maxIdleTime / 2, maxIdleTime / 2, TimeUnit.MILLISECONDS);
            LOGGER.info("Repository pool initialized with max size: {}, max idle time: {}s", 
                maxPoolSize, maxIdleTime / 1000);
        } else {
            LOGGER.info("Repository pool disabled");
        }
    }

    /**
     * 借用Repository实例，如果缓存中有则复用，否则创建新实例
     */
    public Repository borrowRepository(String repoPath) throws IOException {
        if (!enablePool) {
            return createNewRepository(repoPath);
        }

        RepositoryWrapper wrapper = repositoryCache.get(repoPath);
        if (wrapper != null && !wrapper.isClosed()) {
            wrapper.updateLastAccessTime();
            wrapper.incrementRefCount();
            LOGGER.debug("Repository {} borrowed from pool, ref count: {}", repoPath, wrapper.getRefCount());
            return wrapper.getRepository();
        }

        // 缓存中没有或已关闭，创建新实例
        Repository newRepository = createNewRepository(repoPath);
        if (repositoryCache.size() < maxPoolSize) {
            RepositoryWrapper newWrapper = new RepositoryWrapper(newRepository, System.currentTimeMillis());
            repositoryCache.put(repoPath, newWrapper);
            newWrapper.incrementRefCount();
            LOGGER.debug("Repository {} created and added to pool, current pool size: {}", 
                repoPath, repositoryCache.size());
        } else {
            LOGGER.debug("Repository pool full, creating non-pooled repository for {}", repoPath);
        }
        return newRepository;
    }

    /**
     * 归还Repository实例
     */
    public void returnRepository(String repoPath, Repository repository) {
        if (!enablePool || repository == null) {
            safeCloseRepository(repository);
            return;
        }

        RepositoryWrapper wrapper = repositoryCache.get(repoPath);
        if (wrapper != null && wrapper.getRepository() == repository) {
            wrapper.decrementRefCount();
            LOGGER.debug("Repository {} returned to pool, ref count: {}", repoPath, wrapper.getRefCount());
        } else {
            // 不在池中的Repository，直接关闭
            safeCloseRepository(repository);
        }
    }

    /**
     * 强制关闭并移除Repository实例
     */
    public void removeRepository(String repoPath) {
        RepositoryWrapper wrapper = repositoryCache.remove(repoPath);
        if (wrapper != null) {
            wrapper.close();
            LOGGER.debug("Repository {} removed from pool", repoPath);
        }
    }

    /**
     * 清理空闲的Repository实例
     */
    private void cleanupIdleRepositories() {
        if (!enablePool || !cleanupLock.tryLock()) {
            return;
        }

        try {
            long currentTime = System.currentTimeMillis();
            int removedCount = 0;
            
            for (Map.Entry<String, RepositoryWrapper> entry : repositoryCache.entrySet()) {
                RepositoryWrapper wrapper = entry.getValue();
                if (wrapper.isIdle(currentTime, maxIdleTime) && wrapper.getRefCount() == 0) {
                    wrapper.close();
                    repositoryCache.remove(entry.getKey());
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                LOGGER.debug("Cleaned up {} idle repositories from pool, current pool size: {}", 
                    removedCount, repositoryCache.size());
            }
        } finally {
            cleanupLock.unlock();
        }
    }

    /**
     * 创建新的Repository实例
     */
    private Repository createNewRepository(String repoPath) throws IOException {
        if (repoPath == null || repoPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository path cannot be null or empty");
        }

        File repoDirectory = new File(repoPath.trim());
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .setMustExist(true)
                .findGitDir(repoDirectory);

        if (builder.getGitDir() == null) {
            File gitDirCandidate = new File(repoDirectory, ".git");
            if (gitDirCandidate.isDirectory()) {
                builder.setGitDir(gitDirCandidate);
            } else {
                builder.setGitDir(repoDirectory);
            }
        }

        try {
            Repository repository = builder.build();
            LOGGER.debug("Created new repository instance for path: {}", repoPath);
            return repository;
        } catch (RepositoryNotFoundException ex) {
            LOGGER.error("Repository not found at path: {}", repoPath);
            throw ex;
        }
    }

    /**
     * 安全关闭Repository实例
     */
    private void safeCloseRepository(Repository repository) {
        if (repository != null) {
            try {
                repository.close();
                LOGGER.debug("Repository closed safely");
            } catch (Exception ex) {
                LOGGER.warn("Error closing repository: {}", ex.getMessage());
            }
        }
    }

    /**
     * 获取当前池状态信息
     */
    public PoolStatus getPoolStatus() {
        if (!enablePool) {
            return new PoolStatus(0, 0, 0, false);
        }

        int totalRepositories = repositoryCache.size();
        int activeRepositories = (int) repositoryCache.values().stream()
                .filter(wrapper -> wrapper.getRefCount() > 0)
                .count();
        int idleRepositories = totalRepositories - activeRepositories;

        return new PoolStatus(totalRepositories, activeRepositories, idleRepositories, true);
    }

    /**
     * 关闭池并清理所有资源
     */
    public void shutdown() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        for (RepositoryWrapper wrapper : repositoryCache.values()) {
            wrapper.close();
        }
        repositoryCache.clear();
        LOGGER.info("Repository pool shutdown completed");
    }

    /**
     * Repository包装类，用于管理引用计数和访问时间
     */
    private static class RepositoryWrapper {
        private final Repository repository;
        private volatile long lastAccessTime;
        private volatile int refCount;
        private volatile boolean closed;

        public RepositoryWrapper(Repository repository, long lastAccessTime) {
            this.repository = repository;
            this.lastAccessTime = lastAccessTime;
            this.refCount = 0;
            this.closed = false;
        }

        public Repository getRepository() {
            return closed ? null : repository;
        }

        public void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void incrementRefCount() {
            this.refCount++;
        }

        public void decrementRefCount() {
            if (this.refCount > 0) {
                this.refCount--;
            }
        }

        public int getRefCount() {
            return refCount;
        }

        public boolean isIdle(long currentTime, long maxIdleTime) {
            return (currentTime - lastAccessTime) > maxIdleTime;
        }

        public void close() {
            if (!closed) {
                safeCloseRepository(repository);
                closed = true;
            }
        }

        public boolean isClosed() {
            return closed;
        }

        private void safeCloseRepository(Repository repo) {
            if (repo != null) {
                try {
                    repo.close();
                } catch (Exception ex) {
                    LOGGER.warn("Error closing repository in wrapper: {}", ex.getMessage());
                }
            }
        }
    }

    /**
     * 池状态信息
     */
    public static class PoolStatus {
        private final int totalRepositories;
        private final int activeRepositories;
        private final int idleRepositories;
        private final boolean enabled;

        public PoolStatus(int totalRepositories, int activeRepositories, int idleRepositories, boolean enabled) {
            this.totalRepositories = totalRepositories;
            this.activeRepositories = activeRepositories;
            this.idleRepositories = idleRepositories;
            this.enabled = enabled;
        }

        public int getTotalRepositories() {
            return totalRepositories;
        }

        public int getActiveRepositories() {
            return activeRepositories;
        }

        public int getIdleRepositories() {
            return idleRepositories;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public String toString() {
            return String.format("PoolStatus{enabled=%s, total=%d, active=%d, idle=%d}", 
                enabled, totalRepositories, activeRepositories, idleRepositories);
        }
    }
}
