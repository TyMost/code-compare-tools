package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.domain.migration.MigrationTask;
import com.example.migratediff.infrastructure.persistence.MigrationRepository;
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
 * 基于文件系统的 MigrationTask 仓储实现。
 */
@Repository
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemMigrationRepository implements MigrationRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemMigrationRepository.class);
    private static final String MIGRATION_DIRECTORY = "migration";
    private static final String TASK_FILE_NAME = "task.json";
    private static final String DELTA_FILE_NAME = "delta-group.json";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemMigrationRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        storageSupport.ensureDirectory(storageSupport.resolve(MIGRATION_DIRECTORY));
    }

    @Override
    public MigrationTask save(MigrationTask task) {
        lock.writeLock().lock();
        try {
            String taskId = IdentifierUtils.requireText(task.getId(), "保存 MigrationTask 前需提供 id");
            Path taskDirectory = storageSupport.resolve(MIGRATION_DIRECTORY, taskId);
            storageSupport.ensureDirectory(taskDirectory);

            MigrationTask taskWithoutDelta = new MigrationTask(
                    task.getId(),
                    task.getTaskName(),
                    task.getCreatedAt(),
                    task.getStatus(),
                    null
            );
            storageSupport.writeJson(taskDirectory.resolve(TASK_FILE_NAME), taskWithoutDelta);

            DeltaGroup deltaGroup = task.getDeltaGroup();
            Path deltaFile = taskDirectory.resolve(DELTA_FILE_NAME);
            if (deltaGroup != null) {
                storageSupport.writeJson(deltaFile, deltaGroup);
            } else {
                storageSupport.deleteIfExists(deltaFile);
            }
            log.debug("已持久化 MigrationTask: {}", taskId);
            return task;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<MigrationTask> findById(String id) {
        lock.readLock().lock();
        try {
            Path taskDirectory = storageSupport.resolve(MIGRATION_DIRECTORY, id);
            Path taskFile = taskDirectory.resolve(TASK_FILE_NAME);
            Optional<MigrationTask> optional = storageSupport.readJson(taskFile, MigrationTask.class);
            if (!optional.isPresent()) {
                return Optional.empty();
            }
            MigrationTask task = optional.get();
            storageSupport.readJson(taskDirectory.resolve(DELTA_FILE_NAME), DeltaGroup.class)
                    .map(this::normalizeDeltaGroup)
                    .ifPresent(task::setDeltaGroup);
            return Optional.of(normalizeTask(task));
        } finally {
            lock.readLock().unlock();
        }
    }

    private MigrationTask normalizeTask(MigrationTask task) {
        DeltaGroup deltaGroup = task.getDeltaGroup();
        if (deltaGroup != null) {
            task.setDeltaGroup(normalizeDeltaGroup(deltaGroup));
        }
        return task;
    }

    private DeltaGroup normalizeDeltaGroup(DeltaGroup deltaGroup) {
        if (deltaGroup.getIntersectBlocks() == null) {
            deltaGroup.setIntersectBlocks(new ArrayList<>());
        }
        if (deltaGroup.getConflictBlocks() == null) {
            deltaGroup.setConflictBlocks(new ArrayList<>());
        }
        if (deltaGroup.getUniqueBlocks() == null) {
            deltaGroup.setUniqueBlocks(new ArrayList<>());
        }
        deltaGroup.setDeltaO(normalizeDiffFile(deltaGroup.getDeltaO()));
        deltaGroup.setDeltaG(normalizeDiffFile(deltaGroup.getDeltaG()));
        return deltaGroup;
    }

    private DiffFile normalizeDiffFile(DiffFile diffFile) {
        if (diffFile == null) {
            return null;
        }
        List<DiffBlock> blocks = diffFile.getBlocks();
        if (blocks == null) {
            diffFile.setBlocks(new ArrayList<>());
        }
        return diffFile;
    }
}
