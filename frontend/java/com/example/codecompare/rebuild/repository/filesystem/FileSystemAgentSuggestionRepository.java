package com.example.codecompare.rebuild.repository.filesystem;

import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.repository.model.AgentSuggestionRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.util.StringUtils;

/**
 * File-backed repository for Agent suggestions.
 */
public class FileSystemAgentSuggestionRepository implements AgentSuggestionRepository {

    private static final TypeReference<StoredSuggestions> TYPE = new TypeReference<StoredSuggestions>() {
    };

    private final StorageProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, SuggestionState> states = new ConcurrentHashMap<>();

    public FileSystemAgentSuggestionRepository(StorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveAll(String comparisonId, String filePath, List<AgentSuggestionRecord> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        SuggestionState state = stateFor(comparisonId, filePath);
        state.lock.writeLock().lock();
        try {
            suggestions.forEach(state::replace);
            persist(comparisonId, filePath, state.records);
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    @Override
    public List<AgentSuggestionRecord> findLatest(String comparisonId, String filePath) {
        SuggestionState state = stateFor(comparisonId, filePath);
        state.lock.readLock().lock();
        try {
            List<AgentSuggestionRecord> sorted = new ArrayList<>(state.records);
            sorted.sort(Comparator.comparing(AgentSuggestionRecord::getCreatedAt).reversed());
            return sorted;
        } finally {
            state.lock.readLock().unlock();
        }
    }

    @Override
    public Optional<AgentSuggestionRecord> findByDecision(String comparisonId, String decisionId) {
        List<String> keys = states.keySet().stream()
                .filter(key -> key.startsWith(prefix(comparisonId)))
                .collect(Collectors.toList());
        for (String key : keys) {
            SuggestionState state = states.get(key);
            if (state == null) {
                continue;
            }
            state.lock.readLock().lock();
            try {
                Optional<AgentSuggestionRecord> record = state.records.stream()
                        .filter(item -> item.getDecisionId().equals(decisionId))
                        .findFirst();
                if (record.isPresent()) {
                    return record;
                }
            } finally {
                state.lock.readLock().unlock();
            }
        }
        return Optional.empty();
    }

    @Override
    public void deleteByDecision(String comparisonId, String decisionId) {
        List<String> keys = states.keySet().stream()
                .filter(key -> key.startsWith(prefix(comparisonId)))
                .collect(Collectors.toList());
        for (String key : keys) {
            SuggestionState state = states.get(key);
            if (state == null) {
                continue;
            }
            state.lock.writeLock().lock();
            try {
                boolean changed = state.records.removeIf(record -> record.getDecisionId().equals(decisionId));
                if (changed) {
                    persist(state.comparisonId, state.filePath, state.records);
                }
            } finally {
                state.lock.writeLock().unlock();
            }
        }
    }

    @Override
    public void deleteByFile(String comparisonId, String filePath) {
        SuggestionState state = stateFor(comparisonId, filePath);
        state.lock.writeLock().lock();
        try {
            state.records.clear();
            removeFile(comparisonId, filePath);
            states.remove(key(comparisonId, filePath));
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAll(String comparisonId) {
        if (!StringUtils.hasText(comparisonId)) {
            return;
        }
        String prefix = prefix(comparisonId);
        List<String> targets = states.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toList());
        for (String target : targets) {
            SuggestionState state = states.remove(target);
            if (state == null) {
                continue;
            }
            state.lock.writeLock().lock();
            try {
                state.records.clear();
            } finally {
                state.lock.writeLock().unlock();
            }
        }
        Path directory = properties.resolveAgentSuggestions().resolve(safeSegment(comparisonId));
        deleteDirectory(directory);
    }

    private SuggestionState stateFor(String comparisonId, String filePath) {
        return states.computeIfAbsent(key(comparisonId, filePath),
                ignored -> loadState(comparisonId, filePath));
    }

    private SuggestionState loadState(String comparisonId, String filePath) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveAgentSuggestions(), comparisonId, filePath);
        StoredSuggestions stored = JsonStore.read(file, objectMapper, TYPE);
        SuggestionState state = new SuggestionState(comparisonId, filePath);
        if (stored != null && stored.records != null) {
            state.records.addAll(stored.records);
        }
        return state;
    }

    private void persist(String comparisonId, String filePath, List<AgentSuggestionRecord> records) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveAgentSuggestions(), comparisonId, filePath);
        StoredSuggestions stored = new StoredSuggestions();
        stored.records = new ArrayList<>(records);
        JsonStore.write(file, objectMapper, stored, properties.isPrettyPrint());
    }

    private void removeFile(String comparisonId, String filePath) {
        Path file = StorageFileHelper.resolveComparisonFile(
                properties.resolveAgentSuggestions(), comparisonId, filePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete agent suggestion file: " + file, ex);
        }
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
                            throw new IllegalStateException("Failed to delete agent suggestion file: " + path, ex);
                        }
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete agent suggestion directory: " + directory, ex);
        }
    }

    private String safeSegment(String raw) {
        if (raw == null) {
            return "default";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static final class SuggestionState {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final List<AgentSuggestionRecord> records = new ArrayList<>();
        private final String comparisonId;
        private final String filePath;

        private SuggestionState(String comparisonId, String filePath) {
            this.comparisonId = comparisonId;
            this.filePath = filePath.replace('\\', '/');
        }

        private void replace(AgentSuggestionRecord record) {
            records.removeIf(existing -> existing.getDecisionId().equals(record.getDecisionId()));
            records.add(record);
        }
    }

    private static final class StoredSuggestions {
        private List<AgentSuggestionRecord> records = new ArrayList<>();
    }
}
