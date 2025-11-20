package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RevWalk对象池，用于复用RevWalk实例，减少JGit对象创建开销
 * 支持JDK 8兼容性实现
 */
@Component
public class RevWalkPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevWalkPool.class);

    private final BlockingQueue<RevWalkWrapper> pool;
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final AtomicInteger borrowedCount = new AtomicInteger(0);
    private final int maxPoolSize;
    private final boolean enablePool;
    private final long borrowTimeoutMs;

    public RevWalkPool(@Value("${migratediff.performance.revwalk-pool-size:5}") int maxPoolSize,
                       @Value("${migratediff.performance.enable-revwalk-pool:true}") boolean enablePool,
                       @Value("${migratediff.performance.revwalk-borrow-timeout-ms:5000}") long borrowTimeoutMs) {
        this.maxPoolSize = maxPoolSize;
        this.enablePool = enablePool;
        this.borrowTimeoutMs = borrowTimeoutMs;
        this.pool = new LinkedBlockingQueue<>(maxPoolSize);
        
        if (enablePool) {
            LOGGER.info("RevWalk pool initialized with max size: {}, borrow timeout: {}ms", 
                maxPoolSize, borrowTimeoutMs);
        } else {
            LOGGER.info("RevWalk pool disabled");
        }
    }

    /**
     * 从池中借用RevWalk实例
     */
    public RevWalk borrowRevWalk(Repository repository) {
        if (!enablePool) {
            return createNewRevWalk(repository);
        }

        try {
            RevWalkWrapper wrapper = pool.poll(borrowTimeoutMs, TimeUnit.MILLISECONDS);
            if (wrapper != null) {
                // 重置RevWalk到新的Repository
                wrapper.reset(repository);
                borrowedCount.incrementAndGet();
                LOGGER.debug("RevWalk borrowed from pool, current pool size: {}, borrowed count: {}", 
                    pool.size(), borrowedCount.get());
                return wrapper.getRevWalk();
            } else {
                // 池为空，创建新实例
                RevWalk newRevWalk = createNewRevWalk(repository);
                createdCount.incrementAndGet();
                LOGGER.debug("RevWalk pool empty, created new RevWalk. Created count: {}", createdCount.get());
                return newRevWalk;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while borrowing RevWalk from pool");
            return createNewRevWalk(repository);
        }
    }

    /**
     * 归还RevWalk实例到池中
     */
    public void returnRevWalk(RevWalk revWalk) {
        if (!enablePool || revWalk == null) {
            safeCloseRevWalk(revWalk);
            return;
        }

        try {
            // 清理RevWalk状态
            revWalk.reset();
            revWalk.dispose();
            
            RevWalkWrapper wrapper = new RevWalkWrapper(revWalk);
            if (pool.offer(wrapper)) {
                LOGGER.debug("RevWalk returned to pool, current pool size: {}", pool.size());
            } else {
                // 池已满，直接关闭
                safeCloseRevWalk(revWalk);
                LOGGER.debug("RevWalk pool full, discarded RevWalk");
            }
        } catch (Exception ex) {
            LOGGER.warn("Error returning RevWalk to pool: {}", ex.getMessage());
            safeCloseRevWalk(revWalk);
        }
    }

    /**
     * 创建新的RevWalk实例
     */
    private RevWalk createNewRevWalk(Repository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Repository cannot be null");
        }
        return new RevWalk(repository);
    }

    /**
     * 安全关闭RevWalk实例
     */
    private void safeCloseRevWalk(RevWalk revWalk) {
        if (revWalk != null) {
            try {
                revWalk.close();
                LOGGER.debug("RevWalk closed safely");
            } catch (Exception ex) {
                LOGGER.warn("Error closing RevWalk: {}", ex.getMessage());
            }
        }
    }

    /**
     * 清空池并关闭所有RevWalk实例
     */
    public void clear() {
        RevWalkWrapper wrapper;
        int closedCount = 0;
        while ((wrapper = pool.poll()) != null) {
            safeCloseRevWalk(wrapper.getRevWalk());
            closedCount++;
        }
        LOGGER.info("RevWalk pool cleared, closed {} instances", closedCount);
    }

    /**
     * 获取池状态信息
     */
    public PoolStatus getPoolStatus() {
        return new PoolStatus(
            enablePool,
            pool.size(),
            maxPoolSize,
            createdCount.get(),
            borrowedCount.get()
        );
    }

    /**
     * 关闭池并清理所有资源
     */
    public void shutdown() {
        clear();
        LOGGER.info("RevWalk pool shutdown completed");
    }

    /**
     * RevWalk包装类
     */
    private static class RevWalkWrapper {
        private final RevWalk revWalk;
        private volatile long createdTime;

        public RevWalkWrapper(RevWalk revWalk) {
            this.revWalk = revWalk;
            this.createdTime = System.currentTimeMillis();
        }

        public RevWalk getRevWalk() {
            return revWalk;
        }

        public void reset(Repository repository) {
            if (revWalk != null && repository != null) {
                try {
                    revWalk.reset();
                    // 注意：RevWalk不能简单地更换Repository，这里只是重置状态
                    // 实际使用时需要确保Repository是同一个或重新创建RevWalk
                } catch (Exception ex) {
                    LOGGER.warn("Error resetting RevWalk: {}", ex.getMessage());
                }
            }
        }

        public long getAge() {
            return System.currentTimeMillis() - createdTime;
        }
    }

    /**
     * 池状态信息
     */
    public static class PoolStatus {
        private final boolean enabled;
        private final int currentSize;
        private final int maxSize;
        private final int totalCreated;
        private final int totalBorrowed;

        public PoolStatus(boolean enabled, int currentSize, int maxSize, int totalCreated, int totalBorrowed) {
            this.enabled = enabled;
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.totalCreated = totalCreated;
            this.totalBorrowed = totalBorrowed;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public int getTotalCreated() {
            return totalCreated;
        }

        public int getTotalBorrowed() {
            return totalBorrowed;
        }

        @Override
        public String toString() {
            return String.format("RevWalkPoolStatus{enabled=%s, current=%d/%d, created=%d, borrowed=%d}", 
                enabled, currentSize, maxSize, totalCreated, totalBorrowed);
        }
    }
}
