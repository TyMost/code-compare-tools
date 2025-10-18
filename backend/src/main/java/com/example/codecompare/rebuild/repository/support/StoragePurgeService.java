package com.example.codecompare.rebuild.repository.support;

import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.FileMetadataRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.scanning.ScanResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 提供项目级别的存储清理能力，在重新加载流程中确保旧数据被彻底移除。
 */
@Component
public class StoragePurgeService {

    private static final Logger log = LoggerFactory.getLogger(StoragePurgeService.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final DiffSnapshotRepository diffSnapshotRepository;
    private final BlockDecisionRepository blockDecisionRepository;
    private final AgentSuggestionRepository agentSuggestionRepository;
    private final ScanResultRepository scanResultRepository;
    private final StorageProperties storageProperties;

    public StoragePurgeService(FileMetadataRepository fileMetadataRepository,
                               DiffSnapshotRepository diffSnapshotRepository,
                               BlockDecisionRepository blockDecisionRepository,
                               AgentSuggestionRepository agentSuggestionRepository,
                               ScanResultRepository scanResultRepository,
                               StorageProperties storageProperties) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.diffSnapshotRepository = diffSnapshotRepository;
        this.blockDecisionRepository = blockDecisionRepository;
        this.agentSuggestionRepository = agentSuggestionRepository;
        this.scanResultRepository = scanResultRepository;
        this.storageProperties = storageProperties;
    }

    /**
     * 清理指定项目在本地存储中的全部持久化数据。
     *
     * @param projectCode 项目标识
     */
    public void purgeAll(String projectCode) {
        if (!StringUtils.hasText(projectCode)) {
            return;
        }
        String trimmed = projectCode.trim();
        log.info("开始清理项目的持久化数据，projectCode={}", trimmed);
        // 清理内存态与持久化文件
        fileMetadataRepository.deleteProject(trimmed);
        deleteIfExists(resolveMetadataFile(trimmed));

        scanResultRepository.deleteSummary(trimmed);

        diffSnapshotRepository.deleteAll(trimmed);
        deleteDirectory(storageProperties.resolveDiffSnapshots().resolve(safeSegment(trimmed)));

        blockDecisionRepository.deleteAll(trimmed);
        deleteDirectory(storageProperties.resolveBlockDecisions().resolve(safeSegment(trimmed)));

        agentSuggestionRepository.deleteAll(trimmed);
        deleteDirectory(storageProperties.resolveAgentSuggestions().resolve(safeSegment(trimmed)));
    }

    private Path resolveMetadataFile(String projectCode) {
        String safeProject = safeSegment(projectCode);
        return storageProperties.resolveFileMetadata().resolve(safeProject + ".json");
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(this::deleteIfExists);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to purge directory: " + directory, ex);
        }
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete path: " + path, ex);
        }
    }

    private String safeSegment(String raw) {
        if (raw == null) {
            return "default";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
