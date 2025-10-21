package com.example.codecompare.rebuild.repository.filesystem;

import com.example.codecompare.rebuild.repository.FileMetadataRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

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

public class FileSystemFileMetadataRepository implements FileMetadataRepository {

    private static final TypeReference<StoredProject> TYPE = new TypeReference<StoredProject>() {
    };

    private final StorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, ProjectState> states = new ConcurrentHashMap<>();

    public FileSystemFileMetadataRepository(StorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = configureMapper(objectMapper);
    }

    @Override
    public FileRecord save(FileRecord record) {
        Assert.notNull(record, "record must not be null");
        ProjectState state = stateFor(record.getProjectCode());
        state.lock.writeLock().lock();
        try {
            state.insert(record);
            persist(record.getProjectCode(), state);
            return record;
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    @Override
    public List<FileRecord> saveAll(Collection<FileRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<FileRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(FileRecord::getProjectCode));
        List<FileRecord> result = new ArrayList<>(records.size());
        grouped.forEach((project, projectRecords) -> {
            ProjectState state = stateFor(project);
            state.lock.writeLock().lock();
            try {
                projectRecords.forEach(state::insert);
                persist(project, state);
                result.addAll(projectRecords);
            } finally {
                state.lock.writeLock().unlock();
            }
        });
        return result;
    }

    @Override
    public Optional<FileRecord> findLatest(String projectCode, String path) {
        ProjectState state = stateFor(projectCode);
        state.lock.readLock().lock();
        try {
            return Optional.ofNullable(state.latestByPath.get(normalizeKey(projectCode, path)));
        } finally {
            state.lock.readLock().unlock();
        }
    }

    @Override
    public List<FileRecord> findLatestByProject(String projectCode) {
        ProjectState state = stateFor(projectCode);
        state.lock.readLock().lock();
        try {
            return new ArrayList<>(state.latestByPath.values());
        } finally {
            state.lock.readLock().unlock();
        }
    }

    @Override
    public PageResult<FileRecord> findHistory(String projectCode, PageRequest pageRequest) {
        ProjectState state = stateFor(projectCode);
        state.lock.readLock().lock();
        try {
            List<FileRecord> sorted = state.records.stream()
                    .sorted(byScanTimeDescending())
                    .collect(Collectors.toList());
            return page(sorted, pageRequest);
        } finally {
            state.lock.readLock().unlock();
        }
    }

    @Override
    public void deletePaths(String projectCode, Collection<String> paths) {
        if (CollectionUtils.isEmpty(paths)) {
            return;
        }
        ProjectState state = stateFor(projectCode);
        state.lock.writeLock().lock();
        try {
            List<String> normalizedKeys = paths.stream()
                    .filter(path -> path != null && !path.isEmpty())
                    .map(path -> normalizeKey(projectCode, path))
                    .collect(Collectors.toList());
            if (normalizedKeys.isEmpty()) {
                return;
            }
            java.util.Set<String> keySet = new java.util.HashSet<>(normalizedKeys);
            state.records.removeIf(record -> keySet.contains(
                    normalizeKey(record.getProjectCode(), record.getPath())));
            keySet.forEach(key -> {
                state.historyByPath.remove(key);
                state.latestByPath.remove(key);
            });
            persist(projectCode, state);
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    @Override
    public PageResult<FileRecord> findHistory(String projectCode, String path, PageRequest pageRequest) {
        ProjectState state = stateFor(projectCode);
        state.lock.readLock().lock();
        try {
            List<FileRecord> history = state.historyByPath.getOrDefault(normalizeKey(projectCode, path), Collections.emptyList());
            List<FileRecord> sorted = history.stream()
                    .sorted(byScanTimeDescending())
                    .collect(Collectors.toList());
            return page(sorted, pageRequest);
        } finally {
            state.lock.readLock().unlock();
        }
    }

    @Override
    public void deleteProject(String projectCode) {
        ProjectState state = stateFor(projectCode);
        state.lock.writeLock().lock();
        try {
            state.clear();
            persist(projectCode, state);
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    @Override
    public void purgeOlderThan(String projectCode, Instant threshold) {
        if (threshold == null) {
            return;
        }
        ProjectState state = stateFor(projectCode);
        state.lock.writeLock().lock();
        try {
            state.removeOlderThan(threshold);
            persist(projectCode, state);
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    private ProjectState stateFor(String projectCode) {
        return states.computeIfAbsent(projectCode, this::loadState);
    }

    private ProjectState loadState(String projectCode) {
        Path file = StorageFileHelper.resolveProjectFile(properties, projectCode);
        StoredProject stored = JsonStore.read(file, objectMapper, TYPE);
        ProjectState state = new ProjectState();
        if (stored != null && stored.records != null) {
            stored.records.forEach(state::insert);
        }
        return state;
    }

    private void persist(String projectCode, ProjectState state) {
        Path file = StorageFileHelper.resolveProjectFile(properties, projectCode);
        StoredProject stored = new StoredProject();
        stored.projectCode = projectCode;
        stored.records = new ArrayList<>(state.records);
        JsonStore.write(file, objectMapper, stored, properties.isPrettyPrint());
    }

    private Comparator<FileRecord> byScanTimeDescending() {
        return Comparator.comparing(FileRecord::getScannedAt, Comparator.nullsLast(Instant::compareTo)).reversed();
    }

    private static String normalizeKey(String projectCode, String path) {
        String safePath = path == null ? "" : path.replace('\\', '/');
        return projectCode + "::" + safePath;
    }

    private PageResult<FileRecord> page(List<FileRecord> source, PageRequest request) {
        if (source.isEmpty()) {
            return PageResult.empty(request);
        }
        int fromIndex = Math.min(request.getOffset(), source.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), source.size());
        List<FileRecord> subList = source.subList(fromIndex, toIndex);
        return PageResult.of(new ArrayList<>(subList), source.size(), request);
    }

    private static final class ProjectState {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final List<FileRecord> records = new ArrayList<>();
        private final Map<String, List<FileRecord>> historyByPath = new ConcurrentHashMap<>();
        private final Map<String, FileRecord> latestByPath = new ConcurrentHashMap<>();

        private void insert(FileRecord record) {
            records.removeIf(existing -> existing.getId().equals(record.getId()));
            records.add(record);
            String key = normalizeKey(record.getProjectCode(), record.getPath());
            historyByPath.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(record);
            historyByPath.get(key).sort(Comparator.comparing(FileRecord::getScannedAt, Comparator.nullsFirst(Instant::compareTo)));
            FileRecord currentLatest = latestByPath.get(key);
            if (currentLatest == null || isAfter(record.getScannedAt(), currentLatest.getScannedAt())) {
                latestByPath.put(key, record);
            }
        }

        private void removeOlderThan(Instant threshold) {
            records.removeIf(record -> isBefore(record.getScannedAt(), threshold));
            historyByPath.replaceAll((key, value) -> value.stream()
                    .filter(record -> !isBefore(record.getScannedAt(), threshold))
                    .collect(Collectors.toCollection(ArrayList::new)));
            latestByPath.replaceAll((key, value) -> historyByPath.getOrDefault(key, Collections.emptyList()).stream()
                    .max(Comparator.comparing(FileRecord::getScannedAt, Comparator.nullsFirst(Instant::compareTo)))
                    .orElse(null));
            latestByPath.entrySet().removeIf(entry -> entry.getValue() == null);
        }

        private void clear() {
            records.clear();
            historyByPath.clear();
            latestByPath.clear();
        }

        private boolean isAfter(Instant left, Instant right) {
            if (left == null) {
                return false;
            }
            if (right == null) {
                return true;
            }
            return left.isAfter(right);
        }

        private boolean isBefore(Instant left, Instant right) {
            if (left == null || right == null) {
                return false;
            }
            return left.isBefore(right);
        }
    }

    private static final class StoredProject {
        public String projectCode;
        public List<FileRecord> records = new ArrayList<>();
    }

    private ObjectMapper configureMapper(ObjectMapper mapper) {
        ObjectMapper configured = mapper == null ? new ObjectMapper() : mapper.copy();
        return configured.findAndRegisterModules();
    }
}
