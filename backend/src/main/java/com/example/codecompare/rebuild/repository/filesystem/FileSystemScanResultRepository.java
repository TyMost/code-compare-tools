package com.example.codecompare.rebuild.repository.filesystem;

import com.example.codecompare.rebuild.repository.FileMetadataRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.scanning.ScanResultRepository;
import com.example.codecompare.rebuild.scanning.ScanSummary;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于文件系统的扫描结果仓储实现，复用现有文件存储策略。
 */
public class FileSystemScanResultRepository implements ScanResultRepository {

    private static final TypeReference<StoredSummary> TYPE = new TypeReference<StoredSummary>() {
    };

    private final FileMetadataRepository fileMetadataRepository;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    public FileSystemScanResultRepository(FileMetadataRepository fileMetadataRepository,
                                          StorageProperties storageProperties,
                                          ObjectMapper objectMapper) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.storageProperties = storageProperties;
        this.objectMapper = configureMapper(objectMapper);
    }

    @Override
    public List<FileRecord> saveAll(Collection<FileRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return fileMetadataRepository.saveAll(records);
    }

    @Override
    public Optional<ScanSummary> findLatestSummary(String projectCode) {
        Assert.hasText(projectCode, "projectCode must not be empty");
        Path file = StorageFileHelper.resolveScanSummaryFile(storageProperties, projectCode);
        StoredSummary stored = JsonStore.read(file, objectMapper, TYPE);
        return Optional.ofNullable(stored == null ? null : stored.toSummary());
    }

    @Override
    public void saveSummary(ScanSummary summary) {
        if (summary == null) {
            return;
        }
        Path file = StorageFileHelper.resolveScanSummaryFile(storageProperties, summary.getProjectCode());
        JsonStore.write(file, objectMapper, StoredSummary.from(summary), storageProperties.isPrettyPrint());
    }

    @Override
    public List<FileRecord> findLatestFiles(String projectCode) {
        Assert.hasText(projectCode, "projectCode must not be empty");
        return fileMetadataRepository.findLatestByProject(projectCode);
    }

    @Override
    public void deletePaths(String projectCode, Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        fileMetadataRepository.deletePaths(projectCode, paths);
    }

    @Override
    public void deleteSummary(String projectCode) {
        if (!StringUtils.hasText(projectCode)) {
            return;
        }
        Path file = StorageFileHelper.resolveScanSummaryFile(storageProperties, projectCode);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete scan summary file: " + file, ex);
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class StoredSummary {
        private String projectCode;
        private List<String> scannedRoots = new ArrayList<>();
        private int filesScanned;
        private long totalBytes;
        private Instant startedAt;
        private Instant completedAt;
        private long durationMillis;
        private List<String> warnings = new ArrayList<>();
        private Map<String, String> baseCommits = new LinkedHashMap<>();
        private Map<String, String> latestCommits = new LinkedHashMap<>();
        private String diffEngine;
        private boolean gitIncludeRenames;
        private boolean gitDetectCopies;
        private long gitMaxDiffBytes;
        private long gitMaxFileSizeBytes;

        private ScanSummary toSummary() {
            List<String> roots = scannedRoots == null ? Collections.emptyList() : new ArrayList<>(scannedRoots);
            List<String> resolvedWarnings = warnings == null ? Collections.emptyList() : new ArrayList<>(warnings);
            Duration duration = durationMillis <= 0 ? null : Duration.ofMillis(durationMillis);
            Map<String, String> resolvedBase = baseCommits == null ? Collections.emptyMap() : new LinkedHashMap<>(baseCommits);
            Map<String, String> resolvedLatest = latestCommits == null ? Collections.emptyMap() : new LinkedHashMap<>(latestCommits);
            ScanSummary.DiffConfiguration diffConfiguration = ScanSummary.DiffConfiguration.builder()
                    .gitIncludeRenames(gitIncludeRenames)
                    .gitDetectCopies(gitDetectCopies)
                    .gitMaxDiffBytes(gitMaxDiffBytes)
                    .gitMaxFileSizeBytes(gitMaxFileSizeBytes)
                    .build();

            return ScanSummary.builder()
                    .projectCode(projectCode)
                    .scannedRoots(roots)
                    .filesScanned(filesScanned)
                    .totalBytes(totalBytes)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .duration(duration)
                    .warnings(resolvedWarnings)
                    .baseCommits(resolvedBase)
                    .latestCommits(resolvedLatest)
                    .diffEngine(diffEngine)
                    .diffConfiguration(diffConfiguration)
                    .build();
        }

        private static StoredSummary from(ScanSummary summary) {
            StoredSummary stored = new StoredSummary();
            stored.projectCode = summary.getProjectCode();
            stored.scannedRoots = new ArrayList<>(summary.getScannedRoots());
            stored.filesScanned = summary.getFilesScanned();
            stored.totalBytes = summary.getTotalBytes();
            stored.startedAt = summary.getStartedAt();
            stored.completedAt = summary.getCompletedAt();
            Duration duration = summary.getDuration();
            stored.durationMillis = duration == null ? 0 : duration.toMillis();
            stored.warnings = new ArrayList<>(summary.getWarnings());
            stored.baseCommits = new LinkedHashMap<>(summary.getBaseCommits());
            stored.latestCommits = new LinkedHashMap<>(summary.getLatestCommits());
            stored.diffEngine = summary.getDiffEngine();
            ScanSummary.DiffConfiguration diffConfiguration = summary.getDiffConfiguration();
            stored.gitIncludeRenames = diffConfiguration.isGitIncludeRenames();
            stored.gitDetectCopies = diffConfiguration.isGitDetectCopies();
            stored.gitMaxDiffBytes = diffConfiguration.getGitMaxDiffBytes();
            stored.gitMaxFileSizeBytes = diffConfiguration.getGitMaxFileSizeBytes();
            return stored;
        }
    }

    private ObjectMapper configureMapper(ObjectMapper mapper) {
        ObjectMapper configured = mapper == null ? new ObjectMapper() : mapper.copy();
        return configured.findAndRegisterModules();
    }
}
