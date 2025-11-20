package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.application.scan.ScanReport;
import com.example.migratediff.infrastructure.persistence.ScanReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

@Repository
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemScanReportRepository implements ScanReportRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScanReportRepository.class);
    private static final String REPORT_DIRECTORY = "scan-reports";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemScanReportRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        storageSupport.ensureDirectory(storageSupport.resolve(REPORT_DIRECTORY));
    }

    @Override
    public void save(ScanReport report) {
        if (report == null || !StringUtils.hasText(report.getTaskId())) {
            return;
        }
        lock.writeLock().lock();
        try {
            Path target = resolvePath(report.getTaskId());
            storageSupport.writeJson(target, report);
            log.debug("持久化扫描报告: {}", target);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<ScanReport> find(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Optional.empty();
        }
        lock.readLock().lock();
        try {
            Path target = resolvePath(taskId);
            return storageSupport.readJson(target, ScanReport.class);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return;
        }
        lock.writeLock().lock();
        try {
            Path target = resolvePath(taskId);
            storageSupport.deleteIfExists(target);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try {
            Path directory = storageSupport.resolve(REPORT_DIRECTORY);
            if (!Files.exists(directory)) {
                return;
            }
            try (Stream<Path> stream = Files.list(directory)) {
                stream.filter(Files::isRegularFile).forEach(storageSupport::deleteIfExists);
            } catch (IOException ex) {
                throw new FilePersistenceException("清理扫描报告目录失败: " + directory, ex);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Path resolvePath(String taskId) {
        return storageSupport.resolve(REPORT_DIRECTORY, SafeFileNameEncoder.encode(taskId) + ".json");
    }
}
