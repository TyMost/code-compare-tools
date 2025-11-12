package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.setting.SystemSetting;
import com.example.migratediff.infrastructure.persistence.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于文件系统的系统设置仓储实现。
 */
@Repository
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemSettingRepository implements SettingRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSettingRepository.class);
    private static final String SETTING_DIRECTORY = "setting";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemSettingRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        storageSupport.ensureDirectory(storageSupport.resolve(SETTING_DIRECTORY));
    }

    @Override
    public SystemSetting save(SystemSetting setting) {
        lock.writeLock().lock();
        try {
            String key = IdentifierUtils.requireText(setting.getKey(), "保存 SystemSetting 前需提供 key");
            Path filePath = storageSupport.resolve(SETTING_DIRECTORY, SafeFileNameEncoder.encode(key) + ".json");
            storageSupport.writeJson(filePath, setting);
            log.debug("已持久化 SystemSetting: {}", key);
            return setting;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<SystemSetting> findByKey(String key) {
        lock.readLock().lock();
        try {
            Path filePath = storageSupport.resolve(SETTING_DIRECTORY, SafeFileNameEncoder.encode(key) + ".json");
            return storageSupport.readJson(filePath, SystemSetting.class);
        } finally {
            lock.readLock().unlock();
        }
    }
}
