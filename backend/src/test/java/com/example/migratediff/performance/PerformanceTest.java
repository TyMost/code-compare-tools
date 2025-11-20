package com.example.migratediff.performance;

import com.example.migratediff.infrastructure.PerformanceMonitor;
import com.example.migratediff.infrastructure.git.RepositoryPool;
import com.example.migratediff.infrastructure.git.RevWalkPool;
import com.example.migratediff.infrastructure.git.SnapshotLocator;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 性能测试类，用于验证优化效果
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class PerformanceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceTest.class);

    @Autowired
    private PerformanceMonitor performanceMonitor;

    @Autowired
    private RepositoryPool repositoryPool;

    @Autowired
    private RevWalkPool revWalkPool;

    @Autowired
    private SnapshotLocator snapshotLocator;

    /**
     * 测试Repository池的性能
     */
    @Test
    public void testRepositoryPoolPerformance() throws IOException {
        LOGGER.info("Starting Repository pool performance test...");
        
        String testRepoPath = "D:\\Coding\\code-compare-tools\\examples\\g";
        int iterations = 100;
        
        // 预热
        for (int i = 0; i < 10; i++) {
            Repository repo = repositoryPool.borrowRepository(testRepoPath);
            repositoryPool.returnRepository(testRepoPath, repo);
        }
        
        // 测试池化性能
        long poolStartTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            Repository repo = repositoryPool.borrowRepository(testRepoPath);
            repositoryPool.returnRepository(testRepoPath, repo);
        }
        long poolDuration = System.currentTimeMillis() - poolStartTime;
        
        LOGGER.info("Repository pool test: {} iterations in {}ms, avg: {}ms", 
            iterations, poolDuration, (double) poolDuration / iterations);
        
        // 输出池状态
        LOGGER.info("Repository pool status: {}", repositoryPool.getPoolStatus());
    }

    /**
     * 测试RevWalk池的性能
     */
    @Test
    public void testRevWalkPoolPerformance() throws IOException {
        LOGGER.info("Starting RevWalk pool performance test...");
        
        String testRepoPath = "D:\\Coding\\code-compare-tools\\examples\\g";
        int iterations = 50;
        
        try (Repository repository = repositoryPool.borrowRepository(testRepoPath)) {
            // 预热
            for (int i = 0; i < 5; i++) {
                org.eclipse.jgit.revwalk.RevWalk revWalk = revWalkPool.borrowRevWalk(repository);
                revWalkPool.returnRevWalk(revWalk);
            }
            
            // 测试池化性能
            long poolStartTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                org.eclipse.jgit.revwalk.RevWalk revWalk = revWalkPool.borrowRevWalk(repository);
                revWalkPool.returnRevWalk(revWalk);
            }
            long poolDuration = System.currentTimeMillis() - poolStartTime;
            
            LOGGER.info("RevWalk pool test: {} iterations in {}ms, avg: {}ms", 
                iterations, poolDuration, (double) poolDuration / iterations);
            
            // 输出池状态
            LOGGER.info("RevWalk pool status: {}", revWalkPool.getPoolStatus());
        }
    }

    /**
     * 测试并发扫描性能
     */
    @Test
    public void testConcurrentScanPerformance() {
        LOGGER.info("Starting concurrent scan performance test...");
        
        String testRepoPath = "D:\\Coding\\code-compare-tools\\examples\\g";
        Instant startTime = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant endTime = Instant.now();
        
        int concurrentThreads = 4;
        int operationsPerThread = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        long testStartTime = System.currentTimeMillis();
        
        for (int thread = 0; thread < concurrentThreads; thread++) {
            final int threadId = thread;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        PerformanceMonitor.Timer timer = performanceMonitor.startTimer("concurrent-scan");
                        
                        try (Repository repository = repositoryPool.borrowRepository(testRepoPath)) {
                            // 模拟SnapshotLocator的工作
                            // 这里简化为基本的Repository操作
                            Thread.sleep(100); // 模拟处理时间
                        }
                        
                        long duration = timer.stop();
                        performanceMonitor.recordOperation("concurrent-scan", duration);
                        
                        LOGGER.debug("Thread {} operation {} completed in {}ms", threadId, i, duration);
                    } catch (Exception ex) {
                        LOGGER.error("Thread {} operation {} failed", threadId, i, ex);
                    }
                }
            }, executor);
            
            futures.add(future);
        }
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long testDuration = System.currentTimeMillis() - testStartTime;
        
        LOGGER.info("Concurrent scan test: {} threads * {} operations = {} total operations in {}ms", 
            concurrentThreads, operationsPerThread, concurrentThreads * operationsPerThread, testDuration);
        
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 测试性能监控功能
     */
    @Test
    public void testPerformanceMonitoring() {
        LOGGER.info("Starting performance monitoring test...");
        
        // 执行一些被监控的操作
        for (int i = 0; i < 10; i++) {
            performanceMonitor.executeMonitored("test-operation", () -> {
                Thread.sleep(50); // 模拟操作时间
                return "result-" + i;
            });
        }
        
        // 执行一些会失败的操作
        for (int i = 0; i < 3; i++) {
            try {
                performanceMonitor.executeMonitored("test-operation-failed", () -> {
                    Thread.sleep(20);
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception ex) {
                // 预期的异常
            }
        }
        
        // 输出性能报告
        LOGGER.info("Performance Report:\n{}", performanceMonitor.getPerformanceReport());
        
        // 输出特定操作的指标
        PerformanceMonitor.OperationMetrics metrics = performanceMonitor.getMetrics("test-operation");
        if (metrics != null) {
            LOGGER.info("Test operation metrics: {}", metrics.getSummary());
        }
    }

    /**
     * 压力测试
     */
    @Test
    public void stressTest() throws InterruptedException {
        LOGGER.info("Starting stress test...");
        
        int threads = 8;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        long stressStartTime = System.currentTimeMillis();
        
        for (int thread = 0; thread < threads; thread++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    try {
                        performanceMonitor.executeMonitored("stress-test", () -> {
                            // 模拟各种操作
                            if (i % 4 == 0) {
                                // Repository操作
                                try (Repository repo = repositoryPool.borrowRepository("D:\\Coding\\code-compare-tools\\examples\\g")) {
                                    // 模拟一些处理
                                    Thread.sleep(10);
                                }
                            } else if (i % 4 == 1) {
                                // RevWalk操作
                                try (Repository repo = repositoryPool.borrowRepository("D:\\Coding\\code-compare-tools\\examples\\g")) {
                                    org.eclipse.jgit.revwalk.RevWalk revWalk = revWalkPool.borrowRevWalk(repo);
                                    try {
                                        // 模拟处理
                                        Thread.sleep(5);
                                    } finally {
                                        revWalkPool.returnRevWalk(revWalk);
                                    }
                                }
                            } else if (i % 4 == 2) {
                                // 模拟计算密集型操作
                                Math.sqrt(Math.random() * 1000000);
                                Thread.sleep(15);
                            } else {
                                // 模拟I/O操作
                                Thread.sleep(20);
                            }
                            return "completed";
                        });
                    } catch (Exception ex) {
                        LOGGER.warn("Stress test operation failed", ex);
                    }
                }
            }, executor);
            
            futures.add(future);
        }
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long stressDuration = System.currentTimeMillis() - stressStartTime;
        
        LOGGER.info("Stress test completed: {} threads * {} operations = {} total operations in {}ms", 
            threads, operationsPerThread, threads * operationsPerThread, stressDuration);
        
        // 输出最终的性能报告
        LOGGER.info("Final Performance Report:\n{}", performanceMonitor.getPerformanceReport());
        
        // 输出池的最终状态
        LOGGER.info("Final Repository pool status: {}", repositoryPool.getPoolStatus());
        LOGGER.info("Final RevWalk pool status: {}", revWalkPool.getPoolStatus());
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
