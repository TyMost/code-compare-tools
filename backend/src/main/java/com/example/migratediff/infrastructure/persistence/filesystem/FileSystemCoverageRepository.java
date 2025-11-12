package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.infrastructure.persistence.CoverageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于文件系统的 CoverageSummary 仓储实现。
 */
@Repository
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemCoverageRepository implements CoverageRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemCoverageRepository.class);
    private static final String COVERAGE_DIRECTORY = "coverage";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemCoverageRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        storageSupport.ensureDirectory(storageSupport.resolve(COVERAGE_DIRECTORY));
    }

    @Override
    public void save(CoverageSummary summary) {
        lock.writeLock().lock();
        try {
            String taskId = IdentifierUtils.requireText(summary.getTaskId(), "保存 CoverageSummary 前需提供 taskId");
            Path filePath = storageSupport.resolve(COVERAGE_DIRECTORY, SafeFileNameEncoder.encode(taskId) + ".json");
            storageSupport.writeJson(filePath, summary);
            log.debug("已持久化 CoverageSummary: {}", taskId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<CoverageSummary> findByTaskId(String taskId) {
        lock.readLock().lock();
        try {
            Path filePath = storageSupport.resolve(COVERAGE_DIRECTORY, SafeFileNameEncoder.encode(taskId) + ".json");
            return storageSupport.readJson(filePath, CoverageSummary.class)
                    .map(this::normalizeSummary);
        } finally {
            lock.readLock().unlock();
        }
    }

    private CoverageSummary normalizeSummary(CoverageSummary summary) {
        if (summary.getDetails() == null) {
            summary.setDetails(new ArrayList<>());
        }
        for (CoverageDetail detail : summary.getDetails()) {
            normalizeDetail(detail);
        }
        return summary;
    }

    private void normalizeDetail(CoverageDetail detail) {
        if (detail.getMatchedBlocks() == null) {
            detail.setMatchedBlocks(new ArrayList<DiffBlock>());
        }
        if (detail.getUnmatchedBlocks() == null) {
            detail.setUnmatchedBlocks(new ArrayList<DiffBlock>());
        }
    }
}
