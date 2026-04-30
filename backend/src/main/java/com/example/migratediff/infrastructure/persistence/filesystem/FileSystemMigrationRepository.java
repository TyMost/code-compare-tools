package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.migration.MigrationTask;
import com.example.migratediff.infrastructure.persistence.MigrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemMigrationRepository implements MigrationRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemMigrationRepository.class);
    private static final String MIGRATION_DIRECTORY = "migration";

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
            Path target = resolvePath(task);
            storageSupport.writeJson(target, task);
            log.debug("已持久化迁移任务: {}", target);
            return task;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<MigrationTask> findById(String id) {
        lock.readLock().lock();
        try {
            Path filePath = storageSupport.resolve(MIGRATION_DIRECTORY, SafeFileNameEncoder.encode(id) + ".json");
            return storageSupport.readJson(filePath, MigrationTask.class);
        } finally {
            lock.readLock().unlock();
        }
    }

    private Path resolvePath(MigrationTask task) {
        String safeName = SafeFileNameEncoder.encode(task.getId());
        return storageSupport.resolve(MIGRATION_DIRECTORY, safeName + ".json");
    }
}
