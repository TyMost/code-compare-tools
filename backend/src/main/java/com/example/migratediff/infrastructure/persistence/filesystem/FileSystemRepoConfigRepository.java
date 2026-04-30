package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.config.RepoConfig;
import com.example.migratediff.infrastructure.persistence.RepoConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于文件系统的仓库配置仓储实现
 */
@ConditionalOnProperty(prefix = "file.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@Repository
public class FileSystemRepoConfigRepository implements RepoConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSystemRepoConfigRepository.class);
    private static final String CONFIG_DIRECTORY = "repo-configs";
    private static final String INDEX_FILE = "index.json";

    private final FileStorageSupport storageSupport;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ConcurrentHashMap<String, RepoConfig> memoryCache = new ConcurrentHashMap<>();

    public FileSystemRepoConfigRepository(FileStorageSupport storageSupport) {
        this.storageSupport = storageSupport;
        this.storageSupport.ensureDirectory(storageSupport.resolve(CONFIG_DIRECTORY));
        loadIndexToMemory();
    }

    @Override
    public RepoConfig save(RepoConfig config) {
        lock.writeLock().lock();
        try {
            if (config.getId() == null || config.getId().trim().isEmpty()) {
                config.setId(generateId());
            }
            
            // 设置时间戳
            Instant now = Instant.now();
            if (config.getCreatedAt() == null) {
                config.setCreatedAt(now);
            }
            config.setUpdatedAt(now);

            // 验证配置
            validateConfig(config);

            // 保存到文件
            Path filePath = storageSupport.resolve(CONFIG_DIRECTORY, SafeFileNameEncoder.encode(config.getId()) + ".json");
            storageSupport.writeJson(filePath, config);

            // 更新内存缓存
            memoryCache.put(config.getId(), config);

            // 更新索引
            updateIndex();

            log.debug("已保存仓库配置: {} ({})", config.getName(), config.getId());
            return config;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<RepoConfig> findById(String id) {
        lock.readLock().lock();
        try {
            // 先从内存缓存查找
            RepoConfig cached = memoryCache.get(id);
            if (cached != null) {
                return Optional.of(cached);
            }

            // 从文件加载
            Path filePath = storageSupport.resolve(CONFIG_DIRECTORY, SafeFileNameEncoder.encode(id) + ".json");
            Optional<RepoConfig> config = storageSupport.readJson(filePath, RepoConfig.class);
            
            // 更新内存缓存
            config.ifPresent(c -> memoryCache.put(id, c));
            
            return config;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<RepoConfig> findByName(String name) {
        lock.readLock().lock();
        try {
            return memoryCache.values().stream()
                    .filter(config -> name.equals(config.getName()))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RepoConfig> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(memoryCache.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteById(String id) {
        lock.writeLock().lock();
        try {
            // 删除文件
            Path filePath = storageSupport.resolve(CONFIG_DIRECTORY, SafeFileNameEncoder.encode(id) + ".json");
            try {
                Files.deleteIfExists(filePath);
            } catch (Exception e) {
                log.warn("删除配置文件失败: " + filePath, e);
            }

            // 更新内存缓存
            memoryCache.remove(id);

            // 更新索引
            updateIndex();

            log.debug("已删除仓库配置: {}", id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean existsById(String id) {
        lock.readLock().lock();
        try {
            return memoryCache.containsKey(id) || 
                   Files.exists(storageSupport.resolve(CONFIG_DIRECTORY, SafeFileNameEncoder.encode(id) + ".json"));
        } catch (Exception e) {
            log.warn("检查配置存在性失败: " + id, e);
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByName(String name) {
        lock.readLock().lock();
        try {
            return memoryCache.values().stream()
                    .anyMatch(config -> name.equals(config.getName()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 加载索引到内存
     */
    @SuppressWarnings("unchecked")
    private void loadIndexToMemory() {
        try {
            Path indexPath = storageSupport.resolve(CONFIG_DIRECTORY, INDEX_FILE);
            if (Files.exists(indexPath)) {
                List<RepoConfigIndex> index = storageSupport.readJson(indexPath, List.class)
                        .map(list -> (List<RepoConfigIndex>) list)
                        .orElseGet(ArrayList::new);
                
                for (RepoConfigIndex item : index) {
                    // 只加载索引信息，不加载完整配置
                    RepoConfig config = RepoConfig.builder()
                            .id(item.getId())
                            .name(item.getName())
                            .createdAt(item.getCreatedAt())
                            .updatedAt(item.getUpdatedAt())
                            .build();
                    memoryCache.put(item.getId(), config);
                }
                
                log.info("已加载 {} 个仓库配置索引", index.size());
            }
        } catch (Exception e) {
            log.warn("加载配置索引失败，将扫描目录", e);
            scanAndBuildIndex();
        }
    }

    /**
     * 扫描目录并构建索引
     */
    private void scanAndBuildIndex() {
        try {
            Path configDir = storageSupport.resolve(CONFIG_DIRECTORY);
            if (!Files.exists(configDir)) {
                return;
            }

            Files.list(configDir)
                    .filter(path -> path.toString().endsWith(".json") && !path.getFileName().toString().equals(INDEX_FILE))
                    .forEach(path -> {
                        try {
                            Optional<RepoConfig> config = storageSupport.readJson(path, RepoConfig.class);
                            config.ifPresent(c -> memoryCache.put(c.getId(), c));
                        } catch (Exception e) {
                            log.warn("加载配置文件失败: " + path, e);
                        }
                    });
            
            updateIndex();
            log.info("扫描并构建了 {} 个仓库配置", memoryCache.size());
        } catch (Exception e) {
            log.error("扫描配置目录失败", e);
        }
    }

    /**
     * 更新索引文件
     */
    private void updateIndex() {
        try {
            List<RepoConfigIndex> index = new ArrayList<>();
            for (RepoConfig config : memoryCache.values()) {
                index.add(RepoConfigIndex.builder()
                        .id(config.getId())
                        .name(config.getName())
                        .createdAt(config.getCreatedAt())
                        .updatedAt(config.getUpdatedAt())
                        .build());
            }

            Path indexPath = storageSupport.resolve(CONFIG_DIRECTORY, INDEX_FILE);
            storageSupport.writeJson(indexPath, index);
        } catch (Exception e) {
            log.warn("更新配置索引失败", e);
        }
    }

    /**
     * 生成唯一ID
     */
    private String generateId() {
        return "config-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString((int) (Math.random() * 0xFFFF));
    }

    /**
     * 验证配置
     */
    private void validateConfig(RepoConfig config) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("配置名称不能为空");
        }
        
        if (config.getSource() == null || config.getTarget() == null) {
            throw new IllegalArgumentException("源仓库和目标仓库配置不能为空");
        }
        
        if (config.getSource().getPath() == null || config.getSource().getPath().trim().isEmpty()) {
            throw new IllegalArgumentException("源仓库路径不能为空");
        }
        
        if (config.getTarget().getPath() == null || config.getTarget().getPath().trim().isEmpty()) {
            throw new IllegalArgumentException("目标仓库路径不能为空");
        }
        
        // 检查名称唯一性（排除自己）
        memoryCache.values().stream()
                .filter(c -> !c.getId().equals(config.getId()) && c.getName().equals(config.getName()))
                .findAny()
                .ifPresent(c -> {
                    throw new IllegalArgumentException("配置名称已存在: " + config.getName());
                });
    }

    /**
     * 配置索引项
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RepoConfigIndex {
        private String id;
        private String name;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
