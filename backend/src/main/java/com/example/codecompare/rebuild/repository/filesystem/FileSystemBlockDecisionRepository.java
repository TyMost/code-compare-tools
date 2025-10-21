package com.example.codecompare.rebuild.repository.filesystem;

import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File-backed repository for block decision snapshots.
 */
public class FileSystemBlockDecisionRepository implements BlockDecisionRepository {

    private static final TypeReference<StoredSnapshots> TYPE = new TypeReference<StoredSnapshots>() {
    };

    private final StorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, SnapshotState> states = new ConcurrentHashMap<>();

    public FileSystemBlockDecisionRepository(StorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = configureMapper(objectMapper);
    }

    @Override
    public BlockDecisionSnapshot save(BlockDecisionSnapshot snapshot) {
        Assert.notNull(snapshot, "snapshot must not be null");
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
    public Optional<BlockDecisionSnapshot> findLatest(String comparisonId, String filePath) {
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
    public PageResult<BlockDecisionSnapshot> findHistory(String comparisonId, PageRequest pageRequest) {
        List<BlockDecisionSnapshot> all = collectAll(comparisonId);
        all.sort(byAnalyzedAt());
        return page(all, pageRequest);
    }

    @Override
    public PageResult<BlockDecisionSnapshot> findHistory(String comparisonId, String filePath, PageRequest pageRequest) {
        SnapshotState state = stateFor(comparisonId, filePath);
        state.lock.readLock().lock();
        try {
            List<BlockDecisionSnapshot> sorted = new ArrayList<>(state.snapshots);
            sorted.sort(byAnalyzedAt());
            return page(sorted, pageRequest);
        } finally {
            state.lock.readLock().unlock();
        }
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
        Path directory = properties.resolveBlockDecisions().resolve(safeSegment(comparisonId));
        deleteDirectory(directory);
    }

    @Override
    public void purgeOlderThan(String comparisonId, Instant threshold) {
        if (threshold == null) {
            return;
        }
        List<String> targets = states.keySet().stream()
                .filter(key -> key.startsWith(prefix(comparisonId)))
                .collect(Collectors.toList());
        for (String target : targets) {
            SnapshotState state = states.get(target);
            if (state == null) {
                continue;
            }
            state.lock.writeLock().lock();
            try {
                boolean changed = state.snapshots.removeIf(snapshot -> snapshot.getAnalyzedAt().isBefore(threshold));
                if (changed) {
                    if (state.snapshots.isEmpty()) {
                        removeFile(comparisonId, state.filePath);
                        states.remove(target);
                    } else {
                        persist(comparisonId, state.filePath, state.snapshots);
                    }
                }
            } finally {
                state.lock.writeLock().unlock();
            }
        }
    }

    @Override
    public Set<String> listComparisonIds() {
        Set<String> identifiers = new LinkedHashSet<>();
        for (String key : states.keySet()) {
            int delimiter = key.indexOf("::");
            if (delimiter > 0) {
                identifiers.add(key.substring(0, delimiter));
            }
        }
        Path base = properties.resolveBlockDecisions();
        if (Files.exists(base)) {
            try (Stream<Path> stream = Files.list(base)) {
                stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName() == null ? null : path.getFileName().toString())
                        .filter(item -> item != null && !item.isEmpty())
                        .forEach(identifiers::add);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to list block decision directories: " + base, ex);
            }
        }
        return identifiers;
    }

    private SnapshotState stateFor(String comparisonId, String filePath) {
        return states.computeIfAbsent(key(comparisonId, filePath),
                ignored -> loadState(comparisonId, filePath));
    }

    private SnapshotState loadState(String comparisonId, String filePath) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveBlockDecisions(), comparisonId, filePath);
        StoredSnapshots stored = JsonStore.read(file, objectMapper, TYPE);
        SnapshotState state = new SnapshotState(filePath);
        if (stored != null && stored.snapshots != null) {
            state.snapshots.addAll(stored.snapshots);
            state.snapshots.sort(byAnalyzedAt());
        }
        return state;
    }

    private void persist(String comparisonId, String filePath, List<BlockDecisionSnapshot> snapshots) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveBlockDecisions(), comparisonId, filePath);
        StoredSnapshots stored = new StoredSnapshots();
        stored.snapshots = snapshots == null ? new ArrayList<>() : new ArrayList<>(snapshots);
        JsonStore.write(file, objectMapper, stored, properties.isPrettyPrint());
    }

    private void removeFile(String comparisonId, String filePath) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveBlockDecisions(), comparisonId, filePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete snapshot file: " + file, ex);
        }
    }

    private List<BlockDecisionSnapshot> collectAll(String comparisonId) {
        List<BlockDecisionSnapshot> snapshots = new ArrayList<>();
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

    private void tryLoadFromDisk(String comparisonId, Collection<BlockDecisionSnapshot> target) {
        Path base = properties.resolveBlockDecisions().resolve(safeSegment(comparisonId));
        if (!Files.exists(base)) {
            return;
        }
        try {
            Files.list(base)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        StoredSnapshots stored = JsonStore.read(path, objectMapper, TYPE);
                        if (stored != null && stored.snapshots != null) {
                            target.addAll(stored.snapshots);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read snapshot directory: " + base, ex);
        }
    }

    private Comparator<BlockDecisionSnapshot> byAnalyzedAt() {
        return Comparator.comparing(BlockDecisionSnapshot::getAnalyzedAt, Comparator.nullsLast(Instant::compareTo)).reversed();
    }

    private PageResult<BlockDecisionSnapshot> page(List<BlockDecisionSnapshot> source, PageRequest request) {
        if (source.isEmpty()) {
            return PageResult.empty(request);
        }
        int fromIndex = Math.min(request.getOffset(), source.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), source.size());
        List<BlockDecisionSnapshot> slice = source.subList(fromIndex, toIndex);
        return PageResult.of(new ArrayList<>(slice), source.size(), request);
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
                            throw new IllegalStateException("Failed to delete snapshot file: " + path, ex);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete snapshot directory: " + directory, ex);
        }
    }

    private String safeSegment(String raw) {
        if (raw == null) {
            return "default";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static final class SnapshotState {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final List<BlockDecisionSnapshot> snapshots = new ArrayList<>();
        private final String filePath;

        private SnapshotState(String filePath) {
            this.filePath = filePath.replace('\\', '/');
        }

        private void replace(BlockDecisionSnapshot snapshot) {
            snapshots.removeIf(existing -> existing.getId().equals(snapshot.getId()));
            snapshots.add(snapshot);
            snapshots.sort(Comparator.comparing(BlockDecisionSnapshot::getAnalyzedAt));
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class StoredSnapshots {
        private List<BlockDecisionSnapshot> snapshots = new ArrayList<>();
    }

    private ObjectMapper configureMapper(ObjectMapper mapper) {
        ObjectMapper configured = mapper == null ? new ObjectMapper() : mapper.copy();
        return configured.findAndRegisterModules();
    }
}
