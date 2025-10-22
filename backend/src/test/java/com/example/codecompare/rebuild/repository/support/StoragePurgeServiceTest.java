package com.example.codecompare.rebuild.repository.support;

import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.FileMetadataRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemAgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemBlockDecisionRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemDiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemFileMetadataRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemScanResultRepository;
import com.example.codecompare.rebuild.repository.filesystem.StorageFileHelper;
import com.example.codecompare.rebuild.repository.model.AgentSuggestionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.DiffSnapshotDocument;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.scanning.ScanResultRepository;
import com.example.codecompare.rebuild.scanning.ScanSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePurgeServiceTest {

    @TempDir
    Path workspace;

    @Test
    void purgeAllRemovesPersistedArtifacts() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setLocation(workspace.toString());

        ObjectMapper mapper = new ObjectMapper();
        FileMetadataRepository fileMetadataRepository = new FileSystemFileMetadataRepository(storageProperties, mapper);
        DiffSnapshotRepository diffSnapshotRepository = new FileSystemDiffSnapshotRepository(storageProperties, mapper);
        BlockDecisionRepository blockDecisionRepository = new FileSystemBlockDecisionRepository(storageProperties, mapper);
        AgentSuggestionRepository agentSuggestionRepository = new FileSystemAgentSuggestionRepository(storageProperties, mapper);
        ScanResultRepository scanResultRepository = new FileSystemScanResultRepository(fileMetadataRepository, storageProperties, mapper);
        StoragePurgeService purgeService = new StoragePurgeService(
                fileMetadataRepository,
                diffSnapshotRepository,
                blockDecisionRepository,
                agentSuggestionRepository,
                scanResultRepository,
                storageProperties);

        String project = "demo-project";
        String filePath = "src/Main.java";

        FileRecord record = FileRecord.builder()
                .projectCode(project)
                .path(filePath)
                .language("java")
                .contentHash("hash-1")
                .sizeInBytes(128)
                .lastModified(Instant.now())
                .scannedAt(Instant.now())
                .changeType(FileChangeType.NEW)
                .build();
        fileMetadataRepository.save(record);

        DiffSnapshotDocument snapshotDocument = DiffSnapshotDocument.builder()
                .comparisonId(project)
                .filePath(filePath)
                .sourceProjectCode("source")
                .targetProjectCode("target")
                .totalBlocks(1)
                .unlabeledBlocks(0)
                .decisionIds(Collections.singletonList("decision-1"))
                .build();
        diffSnapshotRepository.save(snapshotDocument);

        BlockDecisionRecord decisionRecord = BlockDecisionRecord.builder()
                .comparisonId(project)
                .filePath(filePath)
                .status("review")
                .riskLevel("medium")
                .action("none")
                .build();
        BlockDecisionSnapshot decisionSnapshot = BlockDecisionSnapshot.builder()
                .comparisonId(project)
                .filePath(filePath)
                .sourceProjectCode("source")
                .targetProjectCode("target")
                .records(Collections.singletonList(decisionRecord))
                .build();
        blockDecisionRepository.save(decisionSnapshot);

        AgentSuggestionRecord suggestionRecord = AgentSuggestionRecord.builder()
                .comparisonId(project)
                .filePath(filePath)
                .decisionId(decisionRecord.getId())
                .code("suggested change")
                .build();
        agentSuggestionRepository.saveAll(project, filePath, Collections.singletonList(suggestionRecord));

        ScanSummary summary = ScanSummary.builder()
                .projectCode(project)
                .addScannedRoot("src")
                .filesScanned(1)
                .totalBytes(128)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        scanResultRepository.saveSummary(summary);

        assertThat(fileMetadataRepository.findLatestByProject(project)).isNotEmpty();
        assertThat(diffSnapshotRepository.findLatest(project, filePath)).isPresent();
        assertThat(blockDecisionRepository.findLatest(project, filePath)).isPresent();
        assertThat(agentSuggestionRepository.findLatest(project, filePath)).isNotEmpty();
        assertThat(scanResultRepository.findLatestSummary(project)).isPresent();

        purgeService.purgeAll(project);

        assertThat(fileMetadataRepository.findLatestByProject(project)).isEmpty();
        assertThat(diffSnapshotRepository.findLatest(project, filePath)).isEmpty();
        assertThat(blockDecisionRepository.findLatest(project, filePath)).isEmpty();
        assertThat(agentSuggestionRepository.findLatest(project, filePath)).isEmpty();
        assertThat(scanResultRepository.findLatestSummary(project)).isEmpty();

        String safeSegment = project.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path metadataFile = storageProperties.resolveFileMetadata().resolve(safeSegment + ".json");
        assertThat(Files.exists(metadataFile)).isFalse();
        assertThat(StorageFileHelper.findComparisonDirectories(
                storageProperties.resolveDiffSnapshots(), project)).isEmpty();
        assertThat(StorageFileHelper.findComparisonDirectories(
                storageProperties.resolveBlockDecisions(), project)).isEmpty();
        assertThat(StorageFileHelper.findComparisonDirectories(
                storageProperties.resolveAgentSuggestions(), project)).isEmpty();
    }
}
