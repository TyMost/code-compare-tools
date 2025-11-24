package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
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

@Component
public class SnapshotLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotLocator.class);

    private final RevWalkPool revWalkPool;
    private final boolean enableParallelProcessing;
    private final int maxConcurrentRefs;
    private final ExecutorService executorService;

    @Autowired
    public SnapshotLocator(RevWalkPool revWalkPool,
                          @Value("${migratediff.performance.parallel-ref-processing:true}") boolean enableParallelProcessing,
                          @Value("${migratediff.performance.max-concurrent-refs:8}") int maxConcurrentRefs) {
        this.revWalkPool = revWalkPool;
        this.enableParallelProcessing = enableParallelProcessing;
        this.maxConcurrentRefs = maxConcurrentRefs;
        this.executorService = enableParallelProcessing ? 
            Executors.newFixedThreadPool(maxConcurrentRefs, new NamedThreadFactory("snapshot-locator")) : null;
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
        
        SnapshotLocatorOptions effectiveOptions = options != null ? options : SnapshotLocatorOptions.builder().build();
        List<Ref> refs = collectRefs(repository, effectiveOptions);
        if (refs.isEmpty()) {
            throw new IllegalStateException("No refs available for snapshot scan");
        }

        LOGGER.info("Starting snapshot locator for {} refs, time range: {} to {}", refs.size(), startTime, endTime);
        
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
                    
                    if (ref == null || ref.getObjectId() == null) {
                        continue;
                    }

                    try {
                        processRef(repository, ref, startTime, endTime, earliestCandidate, latestCandidate);
                    } catch (Exception ex) {
                        exceptionHolder.compareAndSet(null, ex);
                        LOGGER.error("Error processing ref {}: {}", ref.getName(), ex.getMessage(), ex);
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

        for (Ref ref : refs) {
            if (ref == null || ref.getObjectId() == null) {
                continue;
            }
            
            scannedRefs++;
            processRef(repository, ref, startTime, endTime, earliestCandidate, latestCandidate);
        }

        SnapshotCandidate earliest = earliestCandidate.get();
        SnapshotCandidate latest = latestCandidate.get();
        
        if (earliest == null || latest == null) {
            throw new IllegalStateException("No commits found in the requested time range after scanning " + scannedRefs + " refs");
        }

        LOGGER.debug("Sequential snapshot locator scanned {} refs; earliest={}, latest={}", 
            scannedRefs, earliest.getInstant(), latest.getInstant());
        
        return new SnapshotPair(earliest.getObjectId(), earliest.getInstant(), latest.getObjectId(), latest.getInstant());
    }

    /**
     * 处理单个引用，查找时间范围内的提交
     */
    private void processRef(Repository repository, Ref ref, Instant startTime, Instant endTime,
                           AtomicReference<SnapshotCandidate> earliestCandidate,
                           AtomicReference<SnapshotCandidate> latestCandidate) throws IOException {
        
        RevWalk revWalk = null;
        try {
            revWalk = revWalkPool.borrowRevWalk(repository);
            revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
            revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));

            int processedCommits = 0;
            RevCommit commit;
            
            while ((commit = revWalk.next()) != null) {
                processedCommits++;
                Instant commitInstant = Instant.ofEpochSecond(commit.getCommitTime());
                
                // 优化：如果提交时间早于开始时间，且我们已经在寻找最早提交，可以提前退出
                if (commitInstant.isBefore(startTime)) {
                    break;
                }
                
                // 跳过时间窗口之后的提交
                if (commitInstant.isAfter(endTime)) {
                    continue;
                }

                // 更新最新提交候选
                updateLatestCandidate(latestCandidate, commit, commitInstant, ref.getName());
                
                // 更新最早提交候选
                updateEarliestCandidate(earliestCandidate, commit, commitInstant, ref.getName());
            }

            if (processedCommits > 0) {
                LOGGER.debug("Processed {} commits for ref {}, found candidates in time range", processedCommits, ref.getName());
            }
            
        } catch (MissingObjectException ex) {
            // 修复 JGit MissingObjectException: 优雅处理缺失的 Git 对象
            handleMissingObjectException(ex, ref);
            // 不抛出异常，继续处理其他引用
        } catch (IOException ex) {
            // 检查是否是 MissingObjectException 的包装
            if (ex.getCause() instanceof MissingObjectException) {
                handleMissingObjectException((MissingObjectException) ex.getCause(), ref);
            } else if (ex.getMessage() != null && ex.getMessage().contains("Missing unknown")) {
                // 处理 JGit 内部的 MissingObjectException 消息
                MissingObjectException moe = new MissingObjectException(null, ex.getMessage());
                handleMissingObjectException(moe, ref);
            } else {
                throw ex;
            }
        } catch (RevisionSyntaxException ex) {
            LOGGER.warn("Invalid revision syntax for ref {}: {}", ref.getName(), ex.getMessage());
        } finally {
            if (revWalk != null) {
                revWalkPool.returnRevWalk(revWalk);
            }
        }
    }

    /**
     * 处理 MissingObjectException 错误
     */
    private void handleMissingObjectException(MissingObjectException ex, Ref ref) {
        String objectId = ex.getObjectId() != null ? ex.getObjectId().name() : "unknown";
        LOGGER.warn("MissingObjectException caught for ref {}: Missing unknown {}. Skipping this ref and continuing scan.", 
                   ref.getName(), objectId);
        
        // 记录缺失对象信息，但不进行自动修复以避免复杂性
        LOGGER.info("Missing object {} identified in ref {}. This ref will be skipped.", objectId, ref.getName());
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

    private List<Ref> collectRefs(Repository repository, SnapshotLocatorOptions options) throws IOException {
        RefDatabase refDatabase = repository.getRefDatabase();
        Map<String, Ref> refs = new LinkedHashMap<>();
        addRefs(refs, refDatabase.getRefsByPrefix(Constants.R_HEADS));
        if (options.isIncludeRemoteRefs()) {
            addRefs(refs, refDatabase.getRefsByPrefix(Constants.R_REMOTES));
        }
        if (options.isIncludeTags()) {
            addRefs(refs, refDatabase.getRefsByPrefix(Constants.R_TAGS));
        }
        // HEAD is not included in refsByPrefix so we add it explicitly for completeness.
        Ref head = repository.exactRef(Constants.HEAD);
        if (head != null) {
            refs.putIfAbsent(head.getName(), head);
        }
        List<Ref> ordered = new ArrayList<>(refs.values());
        if (options.getMaxRefs() > 0 && ordered.size() > options.getMaxRefs()) {
            LOGGER.warn("Ref list truncated from {} to {} entries to honor snapshot maxRefs", ordered.size(), options.getMaxRefs());
            return new ArrayList<>(ordered.subList(0, options.getMaxRefs()));
        }
        return ordered;
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
