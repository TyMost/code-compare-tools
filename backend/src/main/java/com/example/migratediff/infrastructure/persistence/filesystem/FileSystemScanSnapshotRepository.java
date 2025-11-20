package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.application.scan.ScanSnapshot;
import com.example.migratediff.infrastructure.persistence.ScanSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

@Repository
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemScanSnapshotRepository implements ScanSnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScanSnapshotRepository.class);
    private static final String SNAPSHOT_DIRECTORY = "scan-snapshots";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemScanSnapshotRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        storageSupport.ensureDirectory(storageSupport.resolve(SNAPSHOT_DIRECTORY));
    }

    @Override
    public void save(ScanSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            Path target = resolvePath(snapshot);
            storageSupport.writeJson(target, snapshot);
            log.debug("已持久化扫描快照: {}", target);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ScanSnapshot> findAll() {
        lock.readLock().lock();
        try {
            Path directory = storageSupport.resolve(SNAPSHOT_DIRECTORY);
            if (!Files.exists(directory)) {
                return new ArrayList<>();
            }
            try (Stream<Path> stream = Files.list(directory)) {
                List<ScanSnapshot> snapshots = new ArrayList<>();
                stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .forEach(path -> safeReadSnapshot(path).ifPresent(snapshots::add));
                return snapshots;
            } catch (IOException ex) {
                throw new FilePersistenceException("读取扫描快照目录失败: " + directory, ex);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try {
            Path directory = storageSupport.resolve(SNAPSHOT_DIRECTORY);
            if (!Files.exists(directory)) {
                return;
            }
            try (Stream<Path> stream = Files.list(directory)) {
                stream.filter(Files::isRegularFile)
                        .forEach(storageSupport::deleteIfExists);
            } catch (IOException ex) {
                throw new FilePersistenceException("清理扫描快照目录失败: " + directory, ex);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Path resolvePath(ScanSnapshot snapshot) {
        String baseName = StringUtils.hasText(snapshot.getRepoId())
                ? snapshot.getRepoId()
                : snapshot.getTaskId();
        String safeName = SafeFileNameEncoder.encode(StringUtils.hasText(baseName) ? baseName : "default");
        return storageSupport.resolve(SNAPSHOT_DIRECTORY, safeName + ".json");
    }

    private Optional<ScanSnapshot> safeReadSnapshot(Path path) {
        try {
            return storageSupport.readJson(path, ScanSnapshot.class);
        } catch (FilePersistenceException ex) {
            if (containsMissingClass(ex)) {
                log.warn("检测到失效的扫描快照文件，自动删除: {}", path);
                try {
                    storageSupport.deleteIfExists(path);
                } catch (FilePersistenceException deleteEx) {
                    log.warn("删除失效的扫描快照失败: {}", path, deleteEx);
                }
            } else {
                log.error("读取扫描快照失败: {}", path, ex);
            }
            return Optional.empty();
        }
    }

    private boolean containsMissingClass(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof ClassNotFoundException || throwable instanceof NoClassDefFoundError) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }
}
