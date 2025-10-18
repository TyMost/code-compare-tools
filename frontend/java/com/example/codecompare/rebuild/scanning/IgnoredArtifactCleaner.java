package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.support.IgnorePatternMatcher;
import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.filesystem.StorageFileHelper;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 清理与忽略规则匹配的持久化文件，确保新增的忽略项不会残留历史数据。
 */
public class IgnoredArtifactCleaner {

    private static final Logger log = LoggerFactory.getLogger(IgnoredArtifactCleaner.class);

    private final StorageProperties storageProperties;
    private final ScanResultRepository scanResultRepository;
    private final BlockDecisionRepository blockDecisionRepository;
    private final DiffSnapshotRepository diffSnapshotRepository;
    private final AgentSuggestionRepository agentSuggestionRepository;

    public IgnoredArtifactCleaner(StorageProperties storageProperties,
                                  ScanResultRepository scanResultRepository,
                                  BlockDecisionRepository blockDecisionRepository,
                                  DiffSnapshotRepository diffSnapshotRepository,
                                  AgentSuggestionRepository agentSuggestionRepository) {
        this.storageProperties = storageProperties;
        this.scanResultRepository = scanResultRepository;
        this.blockDecisionRepository = blockDecisionRepository;
        this.diffSnapshotRepository = diffSnapshotRepository;
        this.agentSuggestionRepository = agentSuggestionRepository;
    }

    public void purge(String projectCode, List<String> ignoreGlobs) {
        if (!StringUtils.hasText(projectCode) || CollectionUtils.isEmpty(ignoreGlobs)) {
            return;
        }
        IgnorePatternMatcher matcher = IgnorePatternMatcher.from(ignoreGlobs);
        if (matcher.isEmpty()) {
            return;
        }
        purgeMetadata(projectCode, matcher);
        purgeComparisonDirectory(storageProperties.resolveBlockDecisions(), projectCode, matcher,
                (comparisonId, filePath) -> blockDecisionRepository.delete(comparisonId, filePath));
        purgeComparisonDirectory(storageProperties.resolveDiffSnapshots(), projectCode, matcher,
                (comparisonId, filePath) -> diffSnapshotRepository.delete(comparisonId, filePath));
        purgeComparisonDirectory(storageProperties.resolveAgentSuggestions(), projectCode, matcher,
                (comparisonId, filePath) -> agentSuggestionRepository.deleteByFile(comparisonId, filePath));
    }

    private void purgeMetadata(String projectCode, IgnorePatternMatcher matcher) {
        List<FileRecord> existing = scanResultRepository.findLatestFiles(projectCode);
        if (existing.isEmpty()) {
            return;
        }
        List<String> toRemove = existing.stream()
                .map(FileRecord::getPath)
                .filter(matcher::matches)
                .collect(Collectors.toList());
        if (toRemove.isEmpty()) {
            return;
        }
        scanResultRepository.deletePaths(projectCode, toRemove);
        log.info("Removed {} persisted file records for project {} that match ignore globs", toRemove.size(), projectCode);
    }

    private void purgeComparisonDirectory(Path baseDir,
                                          String projectCode,
                                          IgnorePatternMatcher matcher,
                                          BiConsumer<String, String> deleter) {
        if (baseDir == null || !Files.exists(baseDir)) {
            return;
        }
        try (Stream<Path> comparisonDirs = Files.list(baseDir)) {
            comparisonDirs
                    .filter(Files::isDirectory)
                    .filter(dir -> belongsToProject(dir.getFileName().toString(), projectCode))
                    .forEach(dir -> purgeComparisonFiles(dir, matcher, deleter));
        } catch (IOException ex) {
            log.warn("Failed to traverse directory {} while cleaning ignored artifacts", baseDir, ex);
        }
    }

    private void purgeComparisonFiles(Path comparisonDir,
                                      IgnorePatternMatcher matcher,
                                      BiConsumer<String, String> deleter) {
        String comparisonId = comparisonDir.getFileName().toString();
        Collection<String> removed = new ArrayList<>();
        try (Stream<Path> files = Files.list(comparisonDir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String decoded = StorageFileHelper.decodeFileName(file.getFileName().toString());
                if (!StringUtils.hasText(decoded)) {
                    return;
                }
                if (matcher.matches(decoded)) {
                    deleter.accept(comparisonId, decoded);
                    removed.add(decoded);
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to inspect comparison directory {}", comparisonDir, ex);
        }
        if (!removed.isEmpty()) {
            log.info("Removed {} files from comparison {} because they match ignore globs", removed.size(), comparisonId);
        }
    }

    private boolean belongsToProject(String comparisonId, String projectCode) {
        if (!StringUtils.hasText(projectCode)) {
            return true;
        }
        if (projectCode.equals(comparisonId)) {
            return true;
        }
        return comparisonId.contains(projectCode);
    }
}
