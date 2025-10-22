package com.example.codecompare.rebuild.repository.filesystem;

import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.repository.model.DiffSnapshotDocument;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File-backed repository for diff snapshots.
 */
public class FileSystemDiffSnapshotRepository implements DiffSnapshotRepository {

    private static final TypeReference<StoredDiffSnapshots> TYPE = new TypeReference<StoredDiffSnapshots>() {
    };

    private final StorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, SnapshotState> states = new ConcurrentHashMap<>();

    public FileSystemDiffSnapshotRepository(StorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = configureMapper(objectMapper);
    }

    @Override
    public DiffSnapshotDocument save(DiffSnapshotDocument snapshot) {
        SnapshotState state = stateFor(snapshot.getComparisonId(), snapshot.getFilePath());
        state.lock.writeLock().lock();
        try {
            state.replace(snapshot);
            persist(snapshot.getComparisonId(), snapshot.getFilePath(), state.snapshots);
            return snapshot;
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<DiffSnapshotDocument> findLatest(String comparisonId, String filePath) {
        SnapshotState state = stateFor(comparisonId, filePath);
        state.lock.readLock().lock();
        try {
            if (state.snapshots.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(state.snapshots.get(state.snapshots.size() - 1));
        } finally {
            state.lock.readLock().unlock();
        }
    }

    @Override
    public PageResult<DiffSnapshotDocument> findRecent(String comparisonId, PageRequest pageRequest) {
        List<DiffSnapshotDocument> all = collectAll(comparisonId);
        all.sort(byGeneratedAt());
        return page(all, pageRequest);
    }

    @Override
    public void delete(String comparisonId, String filePath) {
        SnapshotState state = stateFor(comparisonId, filePath);
        state.lock.writeLock().lock();
        try {
            state.snapshots.clear();
            removeFile(comparisonId, filePath);
            states.remove(key(comparisonId, filePath));
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAll(String comparisonId) {
        if (comparisonId == null || comparisonId.trim().isEmpty()) {
            return;
        }
        String prefix = prefix(comparisonId);
        List<String> targets = new ArrayList<>();
        for (String key : states.keySet()) {
            if (key.startsWith(prefix)) {
                targets.add(key);
            }
        }
        for (String target : targets) {
            SnapshotState state = states.remove(target);
            if (state == null) {
                continue;
            }
            state.lock.writeLock().lock();
            try {
                state.snapshots.clear();
            } finally {
                state.lock.writeLock().unlock();
            }
        }
        List<Path> directories = StorageFileHelper.findComparisonDirectories(
                properties.resolveDiffSnapshots(), comparisonId);
        for (Path directory : directories) {
            deleteDirectory(directory);
        }
    }

    @Override
    public void purgeOlderThan(String comparisonId, Instant threshold) {
        if (threshold == null) {
            return;
        }
        List<String> keys = states.keySet().stream()
                .filter(key -> key.startsWith(prefix(comparisonId)))
                .collect(Collectors.toList());
        for (String key : keys) {
            SnapshotState state = states.get(key);
            if (state == null) {
                continue;
            }
            state.lock.writeLock().lock();
            try {
                boolean changed = state.snapshots.removeIf(snapshot -> snapshot.getGeneratedAt().isBefore(threshold));
                if (changed) {
                    if (state.snapshots.isEmpty()) {
                        removeFile(comparisonId, state.filePath);
                        states.remove(key);
                    } else {
                        persist(comparisonId, state.filePath, state.snapshots);
                    }
                }
            } finally {
                state.lock.writeLock().unlock();
            }
        }
    }

    private SnapshotState stateFor(String comparisonId, String filePath) {
        return states.computeIfAbsent(key(comparisonId, filePath),
                ignored -> loadState(comparisonId, filePath));
    }

    private SnapshotState loadState(String comparisonId, String filePath) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveDiffSnapshots(), comparisonId, filePath);
        StoredDiffSnapshots stored = JsonStore.read(file, objectMapper, TYPE);
        SnapshotState state = new SnapshotState(filePath);
        if (stored != null && stored.snapshots != null) {
            state.snapshots.addAll(stored.snapshots);
            state.snapshots.sort(byGeneratedAt());
        }
        return state;
    }

    private void persist(String comparisonId, String filePath, List<DiffSnapshotDocument> snapshots) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveDiffSnapshots(), comparisonId, filePath);
        StoredDiffSnapshots stored = new StoredDiffSnapshots();
        stored.snapshots = snapshots == null ? new ArrayList<>() : new ArrayList<>(snapshots);
        JsonStore.write(file, objectMapper, stored, properties.isPrettyPrint());
    }

    private void removeFile(String comparisonId, String filePath) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveDiffSnapshots(), comparisonId, filePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete diff snapshot file: " + file, ex);
        }
    }

    private List<DiffSnapshotDocument> collectAll(String comparisonId) {
        List<DiffSnapshotDocument> snapshots = new ArrayList<>();
        for (Map.Entry<String, SnapshotState> entry : states.entrySet()) {
            if (!entry.getKey().startsWith(prefix(comparisonId))) {
                continue;
            }
            SnapshotState state = entry.getValue();
            state.lock.readLock().lock();
            try {
                snapshots.addAll(state.snapshots);
            } finally {
                state.lock.readLock().unlock();
            }
        }
        if (snapshots.isEmpty()) {
            tryLoadFromDisk(comparisonId, snapshots);
        }
        return snapshots;
    }

    private void tryLoadFromDisk(String comparisonId, Collection<DiffSnapshotDocument> target) {
        List<Path> directories = StorageFileHelper.findComparisonDirectories(
                properties.resolveDiffSnapshots(), comparisonId);
        if (directories.isEmpty()) {
            return;
        }
        for (Path directory : directories) {
            if (!Files.exists(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(Files::isRegularFile)
                        .forEach(path -> {
                            StoredDiffSnapshots stored = JsonStore.read(path, objectMapper, TYPE);
                            if (stored != null && stored.snapshots != null) {
                                target.addAll(stored.snapshots);
                            }
                        });
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read diff snapshot directory: " + directory, ex);
            }
        }
    }

    private Comparator<DiffSnapshotDocument> byGeneratedAt() {
        return Comparator.comparing(DiffSnapshotDocument::getGeneratedAt, Comparator.nullsLast(Instant::compareTo)).reversed();
    }

    private PageResult<DiffSnapshotDocument> page(List<DiffSnapshotDocument> source, PageRequest request) {
        if (source.isEmpty()) {
            return PageResult.empty(request);
        }
        int fromIndex = Math.min(request.getOffset(), source.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), source.size());
        return PageResult.of(new ArrayList<>(source.subList(fromIndex, toIndex)), source.size(), request);
    }

    private String key(String comparisonId, String filePath) {
        return prefix(comparisonId) + filePath.replace('\\', '/');
    }

    private String prefix(String comparisonId) {
        return comparisonId + "::";
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to delete diff snapshot file: " + path, ex);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete diff snapshot directory: " + directory, ex);
        }
    }

    private static final class SnapshotState {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final List<DiffSnapshotDocument> snapshots = new ArrayList<>();
        private final String filePath;

        private SnapshotState(String filePath) {
            this.filePath = filePath.replace('\\', '/');
        }

        private void replace(DiffSnapshotDocument snapshot) {
            snapshots.removeIf(existing -> existing.getId().equals(snapshot.getId()));
            snapshots.add(snapshot);
            snapshots.sort(Comparator.comparing(DiffSnapshotDocument::getGeneratedAt));
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class StoredDiffSnapshots {
        private List<DiffSnapshotDocument> snapshots = new ArrayList<>();
    }

    private ObjectMapper configureMapper(ObjectMapper mapper) {
        ObjectMapper configured = mapper == null ? new ObjectMapper() : mapper.copy();
        return configured.findAndRegisterModules();
    }
}
