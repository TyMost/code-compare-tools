package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 修复版本的 SnapshotLocator，解决 MissingObjectException 问题
 * 
 * 主要修复：
 * 1. 在解析 commit 前检查对象是否存在
 * 2. 添加了更健壮的错误处理机制
 * 3. 改进了 RevWalk 的资源管理
 * 4. 添加了仓库健康检查
 */
@Component
public class SnapshotLocatorFix {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotLocatorFix.class);

    private final RevWalkPool revWalkPool;
    private final boolean enableParallelProcessing;
    private final int maxConcurrentRefs;
    private final ExecutorService executorService;

    @Autowired
    public SnapshotLocatorFix(RevWalkPool revWalkPool,
                              @Value("${migratediff.performance.parallel-ref-processing:true}") boolean enableParallelProcessing,
                              @Value("${migratediff.performance.max-concurrent-refs:8}") int maxConcurrentRefs) {
        this.revWalkPool = revWalkPool;
        this.enableParallelProcessing = enableParallelProcessing;
        this.maxConcurrentRefs = maxConcurrentRefs;
        this.executorService = enableParallelProcessing ? 
            Executors.newFixedThreadPool(maxConcurrentRefs, new NamedThreadFactory("snapshot-locator-fix")) : null;
    }

    public SnapshotPair locate(Repository repository, Instant startTime, Instant endTime, SnapshotLocatorOptions options) throws IOException {
        long startTimeMs = System.currentTimeMillis();
        
        if (repository == null) {
            throw new IllegalArgumentException("Repository is required");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Snapshot scan requires both startTime and endTime");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }

        // 仓库健康检查
        if (!isRepositoryHealthy(repository)) {
            LOGGER.warn("Repository health check failed, attempting to repair...");
            repairRepository(repository);
        }
        
        SnapshotLocatorOptions effectiveOptions = options != null ? options : SnapshotLocatorOptions.builder().build();
        List<Ref> refs = collectValidRefs(repository, effectiveOptions);
        if (refs.isEmpty()) {
            throw new IllegalStateException("No valid refs available for snapshot scan");
        }

        LOGGER.info("Starting snapshot locator for {} valid refs, time range: {} to {}", refs.size(), startTime, endTime);
        
        SnapshotPair result;
        if (enableParallelProcessing && refs.size() > 1) {
            result = locateParallel(repository, refs, startTime, endTime, effectiveOptions);
        } else {
            result = locateSequential(repository, refs, startTime, endTime, effectiveOptions);
        }

        long duration = System.currentTimeMillis() - startTimeMs;
        LOGGER.info("Snapshot locator completed in {}ms, found earliest={}, latest={}", 
            duration, result.getEarliestInstant(), result.getLatestInstant());
        
        return result;
    }

    /**
     * 检查仓库健康状态
     */
    private boolean isRepositoryHealthy(Repository repository) {
        try {
            // 检查对象目录
            if (!repository.getObjectDatabase().exists()) {
                LOGGER.error("Object database does not exist");
                return false;
            }

            // 检查基本引用
            Ref head = repository.exactRef(Constants.HEAD);
            if (head == null) {
                LOGGER.warn("HEAD reference is missing");
                return false;
            }

            return true;
        } catch (Exception ex) {
            LOGGER.error("Repository health check failed: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 尝试修复仓库
     */
    private void repairRepository(Repository repository) {
        try {
            LOGGER.info("Attempting repository repair...");
            
            // 尝试垃圾回收来清理和压缩对象
            try (Git git = new Git(repository)) {
                git.gc().call();
                LOGGER.info("Repository garbage collection completed");
            }

            // 刷新引用数据库
            repository.getRefDatabase().refresh();
            LOGGER.info("Reference database refreshed");
            
        } catch (Exception ex) {
            LOGGER.error("Repository repair failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 收集有效的引用，过滤掉损坏的引用
     */
    private List<Ref> collectValidRefs(Repository repository, SnapshotLocatorOptions options) throws IOException {
        RefDatabase refDatabase = repository.getRefDatabase();
        Map<String, Ref> validRefs = new LinkedHashMap<>();
        
        // 收集所有引用
        addRefs(validRefs, refDatabase.getRefsByPrefix(Constants.R_HEADS));
        if (options.isIncludeRemoteRefs()) {
            addRefs(validRefs, refDatabase.getRefsByPrefix(Constants.R_REMOTES));
        }
        if (options.isIncludeTags()) {
            addRefs(validRefs, refDatabase.getRefsByPrefix(Constants.R_TAGS));
        }
        
        Ref head = repository.exactRef(Constants.HEAD);
        if (head != null) {
            validRefs.putIfAbsent(head.getName(), head);
        }

        // 验证引用有效性
        List<Ref> refs = new ArrayList<>();
        for (Map.Entry<String, Ref> entry : validRefs.entrySet()) {
            Ref ref = entry.getValue();
            if (isValidRef(repository, ref)) {
                refs.add(ref);
            } else {
                LOGGER.warn("Skipping invalid ref: {} -> {}", entry.getKey(), 
                    ref.getObjectId() != null ? ref.getObjectId().name() : "null");
            }
        }

        if (options.getMaxRefs() > 0 && refs.size() > options.getMaxRefs()) {
            LOGGER.warn("Ref list truncated from {} to {} entries to honor snapshot maxRefs", 
                refs.size(), options.getMaxRefs());
            return new ArrayList<>(refs.subList(0, options.getMaxRefs()));
        }
        
        LOGGER.info("Collected {} valid refs out of {} total refs", refs.size(), validRefs.size());
        return refs;
    }

    /**
     * 验证引用是否有效
     */
    private boolean isValidRef(Repository repository, Ref ref) {
        if (ref == null || ref.getObjectId() == null) {
            return false;
        }

        try {
            ObjectId objectId = ref.getObjectId();
            
            // 尝试解析对象类型，如果对象不存在会抛出异常
            repository.getObjectDatabase().open(objectId).getType();
            return true;
            
        } catch (Exception ex) {
            LOGGER.debug("Failed to validate ref {}: {}", ref.getName(), ex.getMessage());
            return false;
        }
    }

    /**
     * 并行处理多个引用，提升大仓库扫描性能
     */
    private SnapshotPair locateParallel(Repository repository, List<Ref> refs, Instant startTime, Instant endTime, SnapshotLocatorOptions options) {
        AtomicReference<SnapshotCandidate> earliestCandidate = new AtomicReference<>();
        AtomicReference<SnapshotCandidate> latestCandidate = new AtomicReference<>();
        AtomicReference<Exception> exceptionHolder = new AtomicReference<>();

        // 分批处理引用，避免同时处理过多
        int batchSize = Math.min(maxConcurrentRefs, refs.size());
        List<List<Ref>> batches = partitionList(refs, batchSize);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<Ref> batch : batches) {
            CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                for (Ref ref : batch) {
                    if (exceptionHolder.get() != null) {
                        break; // 如果已经有异常，停止处理
                    }
                    
                    try {
                        processRefSafe(repository, ref, startTime, endTime, earliestCandidate, latestCandidate);
                    } catch (Exception ex) {
                        // 对于单个引用的处理失败，记录警告而不是立即失败
                        LOGGER.warn("Error processing ref {} (continuing with other refs): {}", 
                            ref.getName(), ex.getMessage());
                        // 只有在所有引用都失败时才设置异常
                        if (exceptionHolder.get() == null) {
                            exceptionHolder.compareAndSet(null, ex);
                        }
                    }
                }
            }, executorService);
            
            futures.add(batchFuture);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Exception exception = exceptionHolder.get();
        if (exception != null) {
            throw new RuntimeException("Parallel snapshot locator failed", exception);
        }

        SnapshotCandidate earliest = earliestCandidate.get();
        SnapshotCandidate latest = latestCandidate.get();
        
        if (earliest == null || latest == null) {
            throw new IllegalStateException("No commits found in the requested time range");
        }

        return new SnapshotPair(earliest.getObjectId(), earliest.getInstant(), latest.getObjectId(), latest.getInstant());
    }

    /**
     * 顺序处理引用，用于小数量引用或并行处理被禁用的情况
     */
    private SnapshotPair locateSequential(Repository repository, List<Ref> refs, Instant startTime, Instant endTime, SnapshotLocatorOptions options) throws IOException {
        AtomicReference<SnapshotCandidate> earliestCandidate = new AtomicReference<>();
        AtomicReference<SnapshotCandidate> latestCandidate = new AtomicReference<>();
        int scannedRefs = 0;
        int failedRefs = 0;

        for (Ref ref : refs) {
            try {
                processRefSafe(repository, ref, startTime, endTime, earliestCandidate, latestCandidate);
                scannedRefs++;
            } catch (Exception ex) {
                failedRefs++;
                LOGGER.warn("Failed to process ref {} (continuing): {}", ref.getName(), ex.getMessage());
            }
        }

        LOGGER.info("Sequential processing completed: {} successful, {} failed refs", scannedRefs, failedRefs);

        SnapshotCandidate earliest = earliestCandidate.get();
        SnapshotCandidate latest = latestCandidate.get();
        
        if (earliest == null || latest == null) {
            throw new IllegalStateException("No commits found in the requested time range after scanning " + 
                scannedRefs + " refs (" + failedRefs + " failed)");
        }

        LOGGER.debug("Sequential snapshot locator scanned {} refs; earliest={}, latest={}", 
            scannedRefs, earliest.getInstant(), latest.getInstant());
        
        return new SnapshotPair(earliest.getObjectId(), earliest.getInstant(), latest.getObjectId(), latest.getInstant());
    }

    /**
     * 安全地处理单个引用，查找时间范围内的提交
     */
    private void processRefSafe(Repository repository, Ref ref, Instant startTime, Instant endTime,
                               AtomicReference<SnapshotCandidate> earliestCandidate,
                               AtomicReference<SnapshotCandidate> latestCandidate) throws IOException {
        
        if (ref == null || ref.getObjectId() == null) {
            LOGGER.debug("Skipping null ref or ref with null objectId: {}", ref != null ? ref.getName() : "null");
            return;
        }

        ObjectId objectId = ref.getObjectId();
        
        // 再次验证对象存在性（双重检查）
        try {
            repository.getObjectDatabase().open(objectId).getType();
        } catch (Exception ex) {
            LOGGER.warn("Object {} does not exist for ref {}, skipping: {}", objectId.name(), ref.getName(), ex.getMessage());
            return;
        }

        RevWalk revWalk = null;
        try {
            revWalk = revWalkPool.borrowRevWalk(repository);
            revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
            
            // 安全地解析 commit
            RevCommit commit;
            try {
                commit = revWalk.parseCommit(objectId);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse commit {} for ref {}: {}", objectId.name(), ref.getName(), ex.getMessage());
                return;
            }
            
            revWalk.markStart(commit);

            int processedCommits = 0;
            RevCommit currentCommit;
            
            while ((currentCommit = revWalk.next()) != null) {
                processedCommits++;
                Instant commitInstant = Instant.ofEpochSecond(currentCommit.getCommitTime());
                
                // 优化：如果提交时间早于开始时间，且我们已经在寻找最早提交，可以提前退出
                if (commitInstant.isBefore(startTime)) {
                    break;
                }
                
                // 跳过时间窗口之后的提交
                if (commitInstant.isAfter(endTime)) {
                    continue;
                }

                // 更新最新提交候选
                updateLatestCandidate(latestCandidate, currentCommit, commitInstant, ref.getName());
                
                // 更新最早提交候选
                updateEarliestCandidate(earliestCandidate, currentCommit, commitInstant, ref.getName());
            }

            if (processedCommits > 0) {
                LOGGER.debug("Processed {} commits for ref {}, found candidates in time range", processedCommits, ref.getName());
            }
            
        } finally {
            if (revWalk != null) {
                revWalkPool.returnRevWalk(revWalk);
            }
        }
    }

    /**
     * 更新最新提交候选
     */
    private void updateLatestCandidate(AtomicReference<SnapshotCandidate> current, RevCommit commit, Instant commitInstant, String refName) {
        SnapshotCandidate candidate = new SnapshotCandidate(commit.getId(), commitInstant, refName);
        current.updateAndGet(existing -> {
            if (existing == null || commitInstant.isAfter(existing.getInstant())) {
                LOGGER.debug("Latest candidate updated: ref={}, commit={}, time={}", refName, commit.getId().name(), commitInstant);
                return candidate;
            }
            return existing;
        });
    }

    /**
     * 更新最早提交候选
     */
    private void updateEarliestCandidate(AtomicReference<SnapshotCandidate> current, RevCommit commit, Instant commitInstant, String refName) {
        SnapshotCandidate candidate = new SnapshotCandidate(commit.getId(), commitInstant, refName);
        current.updateAndGet(existing -> {
            if (existing == null || commitInstant.isBefore(existing.getInstant())) {
                LOGGER.debug("Earliest candidate updated: ref={}, commit={}, time={}", refName, commit.getId().name(), commitInstant);
                return candidate;
            }
            return existing;
        });
    }

    /**
     * 将列表分割成指定大小的批次
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }

    private void addRefs(Map<String, Ref> sink, List<Ref> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        for (Ref ref : refs) {
            if (ref != null) {
                sink.putIfAbsent(ref.getName(), ref);
            }
        }
    }
}
