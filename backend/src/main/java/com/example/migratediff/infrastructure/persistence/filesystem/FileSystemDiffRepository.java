package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.infrastructure.persistence.DiffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于文件系统的 DiffSummary 仓储实现。
 */
@Repository
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemDiffRepository implements DiffRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemDiffRepository.class);

    private static final String DIFF_DIRECTORY = "diff";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemDiffRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        storageSupport.ensureDirectory(storageSupport.resolve(DIFF_DIRECTORY));
    }

    @Override
    public DiffSummary save(DiffSummary summary) {
        lock.writeLock().lock();
        try {
            String key = buildDiffId(summary);
            Path filePath = storageSupport.resolve(DIFF_DIRECTORY, key + ".json");
            storageSupport.writeJson(filePath, summary);
            log.debug("已持久化 DiffSummary: {}", key);
            return summary;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<DiffSummary> findById(String id) {
        lock.readLock().lock();
        try {
            Path filePath = storageSupport.resolve(DIFF_DIRECTORY, id + ".json");
            return storageSupport.readJson(filePath, DiffSummary.class)
                    .map(this::normalizeSummary);
        } finally {
            lock.readLock().unlock();
        }
    }

    private String buildDiffId(DiffSummary summary) {
        String baseCommit = IdentifierUtils.requireText(
                summary.getBaseCommitId(), "保存 DiffSummary 前需提供 baseCommitId");
        String targetCommit = IdentifierUtils.requireText(
                summary.getTargetCommitId(), "保存 DiffSummary 前需提供 targetCommitId");
        return baseCommit + "_" + targetCommit;
    }

    private DiffSummary normalizeSummary(DiffSummary summary) {
        if (summary.getDiffFiles() == null) {
            summary.setDiffFiles(new ArrayList<>());
        }
        for (DiffFile file : summary.getDiffFiles()) {
            if (file.getBlocks() == null) {
                file.setBlocks(new ArrayList<>());
            }
        }
        return summary;
    }
}
