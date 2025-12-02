package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SnapshotLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotLocator.class);

    public SnapshotPair locate(Repository repository, Instant startTime, Instant endTime, SnapshotLocatorOptions options) throws IOException {
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
            String repoPath = repository.getDirectory() != null ? repository.getDirectory().getAbsolutePath() : "unknown";
            LOGGER.error("No refs found in repository at: {}. This may indicate an uninitialized or empty repository.", repoPath);
            LOGGER.error("Please ensure the repository has been properly initialized with commits using 'git init' and 'git commit'.");
            throw new IllegalStateException("No refs available for snapshot scan. Repository at '" + repoPath + "' may not be properly initialized with any commits.");
        }

        return locateSequential(repository, refs, startTime, endTime, effectiveOptions);
    }

    private SnapshotPair locateSequential(Repository repository, List<Ref> refs, Instant startTime, Instant endTime, SnapshotLocatorOptions options) throws IOException {
        SnapshotCandidate earliestCandidate = null;
        SnapshotCandidate latestCandidate = null;
        int scannedRefs = 0;

        for (Ref ref : refs) {
            if (ref == null || ref.getObjectId() == null) {
                continue;
            }
            
            scannedRefs++;
            
            try (RevWalk revWalk = new RevWalk(repository)) {
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
                    if (latestCandidate == null || commitInstant.isAfter(latestCandidate.getInstant())) {
                        latestCandidate = new SnapshotCandidate(commit.getId(), commitInstant, ref.getName());
                        LOGGER.debug("Latest candidate updated: ref={}, commit={}, time={}", ref.getName(), commit.getId().name(), commitInstant);
                    }
                    
                    // 更新最早提交候选
                    if (earliestCandidate == null || commitInstant.isBefore(earliestCandidate.getInstant())) {
                        earliestCandidate = new SnapshotCandidate(commit.getId(), commitInstant, ref.getName());
                        LOGGER.debug("Earliest candidate updated: ref={}, commit={}, time={}", ref.getName(), commit.getId().name(), commitInstant);
                    }
                }

                if (processedCommits > 0) {
                    LOGGER.debug("Processed {} commits for ref {}, found candidates in time range", processedCommits, ref.getName());
                }
            } catch (RevisionSyntaxException ex) {
                LOGGER.warn("Invalid revision syntax for ref {}: {}", ref.getName(), ex.getMessage());
            }
        }

        if (earliestCandidate == null || latestCandidate == null) {
            throw new IllegalStateException("No commits found in the requested time range after scanning " + scannedRefs + " refs");
        }

        LOGGER.info("快照定位完成 - 扫描了 {} 个引用", scannedRefs);
        LOGGER.info("最早提交: hash={}, 时间={}, 引用={}", 
            earliestCandidate.getObjectId().name(), 
            earliestCandidate.getInstant(), 
            earliestCandidate.getRefName());
        LOGGER.info("最晚提交: hash={}, 时间={}, 引用={}", 
            latestCandidate.getObjectId().name(), 
            latestCandidate.getInstant(), 
            latestCandidate.getRefName());
        
        return new SnapshotPair(earliestCandidate.getObjectId(), earliestCandidate.getInstant(), latestCandidate.getObjectId(), latestCandidate.getInstant());
    }

    private List<Ref> collectRefs(Repository repository, SnapshotLocatorOptions options) throws IOException {
        RefDatabase refDatabase = repository.getRefDatabase();
        Map<String, Ref> refs = new LinkedHashMap<>();
        
        // 收集本地分支
        List<Ref> localRefs = refDatabase.getRefsByPrefix(Constants.R_HEADS);
        addRefs(refs, localRefs);
        LOGGER.debug("收集到 {} 个本地分支引用", localRefs.size());
        
        // 收集远程引用
        if (options.isIncludeRemoteRefs()) {
            List<Ref> remoteRefs = refDatabase.getRefsByPrefix(Constants.R_REMOTES);
            addRefs(refs, remoteRefs);
            LOGGER.debug("收集到 {} 个远程引用", remoteRefs.size());
        }
        
        // 收集标签
        if (options.isIncludeTags()) {
            List<Ref> tagRefs = refDatabase.getRefsByPrefix(Constants.R_TAGS);
            addRefs(refs, tagRefs);
            LOGGER.debug("收集到 {} 个标签引用", tagRefs.size());
        }
        
        // 添加HEAD
        Ref head = repository.exactRef(Constants.HEAD);
        if (head != null) {
            refs.putIfAbsent(head.getName(), head);
            LOGGER.debug("添加HEAD引用");
        }
        
        List<Ref> ordered = new ArrayList<>(refs.values());
        
        if (options.getMaxRefs() > 0 && ordered.size() > options.getMaxRefs()) {
            LOGGER.warn("引用列表被截断：从 {} 个减少到 {} 个（受maxRefs限制）", ordered.size(), options.getMaxRefs());
            return new ArrayList<>(ordered.subList(0, options.getMaxRefs()));
        }
        
        LOGGER.info("总共收集到 {} 个引用用于快照扫描 (包含远程: {}, 包含标签: {})", 
            ordered.size(), options.isIncludeRemoteRefs(), options.isIncludeTags());
        
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
