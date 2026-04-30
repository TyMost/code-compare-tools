package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.infrastructure.persistence.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemRepoRepository implements RepoRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemRepoRepository.class);
    private static final String REPO_DIRECTORY = "repos";

    private final FileStorageSupport storageSupport;
    private final FileRepoKeyResolver keyResolver;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public FileSystemRepoRepository(FileStorageSupport storageSupport, FileRepoKeyResolver keyResolver) {
        this.storageSupport = storageSupport;
        this.keyResolver = keyResolver;
        storageSupport.ensureDirectory(storageSupport.resolve(REPO_DIRECTORY));
    }

    @Override
    public RepoConfig save(RepoConfig repoConfig) {
        lock.writeLock().lock();
        try {
            String key = keyResolver.resolveKey(repoConfig);
            Path target = storageSupport.resolve(REPO_DIRECTORY, SafeFileNameEncoder.encode(key) + ".json");
            storageSupport.writeJson(target, repoConfig);
            log.debug("已持久化仓库配置: {}", target);
            return repoConfig;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<RepoConfig> findAll() {
        lock.readLock().lock();
        try {
            Path directory = storageSupport.resolve(REPO_DIRECTORY);
            if (!java.nio.file.Files.exists(directory)) {
                return new ArrayList<>();
            }
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(directory)) {
                List<RepoConfig> configs = new ArrayList<>();
                stream.filter(java.nio.file.Files::isRegularFile)
                        .forEach(path -> storageSupport.readJson(path, RepoConfig.class).ifPresent(configs::add));
                return configs;
            } catch (java.io.IOException ex) {
                throw new FilePersistenceException("读取仓库配置目录失败: " + directory, ex);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<RepoConfig> findById(String id) {
        lock.readLock().lock();
        try {
            Path filePath = storageSupport.resolve(REPO_DIRECTORY, SafeFileNameEncoder.encode(id) + ".json");
            return storageSupport.readJson(filePath, RepoConfig.class);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteById(String id) {
        lock.writeLock().lock();
        try {
            Path filePath = storageSupport.resolve(REPO_DIRECTORY, SafeFileNameEncoder.encode(id) + ".json");
            storageSupport.deleteIfExists(filePath);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
