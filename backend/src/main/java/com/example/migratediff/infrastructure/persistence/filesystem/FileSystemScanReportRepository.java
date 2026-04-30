package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.application.scan.ScanReport;
import com.example.migratediff.infrastructure.persistence.ScanReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemScanReportRepository implements ScanReportRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemScanReportRepository.class);
    private static final String SCAN_REPORTS_DIRECTORY = "scan-reports";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemScanReportRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        storageSupport.ensureDirectory(storageSupport.resolve(SCAN_REPORTS_DIRECTORY));
    }

    @Override
    public void save(ScanReport report) {
        if (report == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            Path target = resolvePath(report);
            storageSupport.writeJson(target, report);
            log.debug("已持久化扫描报告: {}", target);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<ScanReport> find(String taskId) {
        lock.readLock().lock();
        try {
            Path filePath = storageSupport.resolve(SCAN_REPORTS_DIRECTORY, SafeFileNameEncoder.encode(taskId) + ".json");
            return storageSupport.readJson(filePath, ScanReport.class);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String taskId) {
        lock.writeLock().lock();
        try {
            Path filePath = storageSupport.resolve(SCAN_REPORTS_DIRECTORY, SafeFileNameEncoder.encode(taskId) + ".json");
            storageSupport.deleteIfExists(filePath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try {
            Path directory = storageSupport.resolve(SCAN_REPORTS_DIRECTORY);
            if (java.nio.file.Files.exists(directory)) {
                try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(directory)) {
                    stream.filter(java.nio.file.Files::isRegularFile)
                            .forEach(storageSupport::deleteIfExists);
                } catch (java.io.IOException ex) {
                    throw new FilePersistenceException("清理扫描报告目录失败: " + directory, ex);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Path resolvePath(ScanReport report) {
        String safeName = SafeFileNameEncoder.encode(report.getTaskId());
        return storageSupport.resolve(SCAN_REPORTS_DIRECTORY, safeName + ".json");
    }
}
