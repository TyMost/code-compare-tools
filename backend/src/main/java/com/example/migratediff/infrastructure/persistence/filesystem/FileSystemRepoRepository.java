package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import com.example.migratediff.infrastructure.persistence.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于文件系统的 RepoConfig 仓储实现。
 */
@Repository
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemRepoRepository implements RepoRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemRepoRepository.class);
    private static final String REPO_DIRECTORY = "repo";
    private static final String INDEX_FILE = "index.json";

    private final FileStorageSupport storageSupport;
    private final FileRepoKeyResolver keyResolver;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path indexFilePath;

    public FileSystemRepoRepository(FileStorageSupport storageSupport, FileRepoKeyResolver keyResolver) {
        this.storageSupport = storageSupport;
        this.keyResolver = keyResolver;
        Path repoDirectory = storageSupport.resolve(REPO_DIRECTORY);
        storageSupport.ensureDirectory(repoDirectory);
        this.indexFilePath = repoDirectory.resolve(INDEX_FILE);
        ensureIndexFile();
    }

    @Override
    public RepoConfig save(RepoConfig repoConfig) {
        lock.writeLock().lock();
        try {
            String id = keyResolver.resolveKey(repoConfig);
            Path filePath = storageSupport.resolve(REPO_DIRECTORY, id + ".json");
            storageSupport.writeJson(filePath, repoConfig);

            RepoIndex index = loadIndex();
            RepoIndexEntry entry = new RepoIndexEntry();
            entry.setId(id);
            entry.setAbsolutePath(resolveAbsolutePath(repoConfig.getRepoPath()));
            entry.setBranchFrom(resolveBranchName(repoConfig.getBranchFrom()));
            entry.setBranchTo(resolveBranchName(repoConfig.getBranchTo()));
            entry.setUpdatedAt(Instant.now());
            index.upsert(id, entry);
            storageSupport.writeJson(indexFilePath, index);

            log.debug("已持久化 RepoConfig: {}", id);
            return repoConfig;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<RepoConfig> findById(String id) {
        lock.readLock().lock();
        try {
            Path filePath = storageSupport.resolve(REPO_DIRECTORY, id + ".json");
            return storageSupport.readJson(filePath, RepoConfig.class);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RepoConfig> findAll() {
        lock.readLock().lock();
        try {
            RepoIndex index = loadIndex();
            index.sortByUpdatedAtDesc();
            List<RepoConfig> result = new ArrayList<>();
            for (RepoIndexEntry entry : index.getEntries()) {
                Path filePath = storageSupport.resolve(REPO_DIRECTORY, entry.getId() + ".json");
                storageSupport.readJson(filePath, RepoConfig.class).ifPresent(result::add);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    private RepoIndex loadIndex() {
        return storageSupport.readJson(indexFilePath, RepoIndex.class)
                .orElseGet(RepoIndex::new);
    }

    private void ensureIndexFile() {
        if (!storageSupport.readJson(indexFilePath, RepoIndex.class).isPresent()) {
            storageSupport.writeJson(indexFilePath, new RepoIndex());
        }
    }

    private String resolveBranchName(RepoBranch branch) {
        return branch != null ? branch.getName() : null;
    }

    private String resolveAbsolutePath(RepoPath repoPath) {
        return repoPath != null ? repoPath.getAbsolutePath() : null;
    }
}
