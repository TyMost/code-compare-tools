package com.example.codecompare.rebuild.agent;

import com.example.codecompare.rebuild.agent.config.MigrationAnnotationProperties;
import com.example.codecompare.rebuild.agent.migration.annotation.AnnotationRenderingService;
import com.example.codecompare.rebuild.agent.migration.diff.DiffSynchronizationService;
import com.example.codecompare.rebuild.agent.migration.io.AnnotatedFileWriter;
import com.example.codecompare.rebuild.agent.migration.io.ProjectFileResolver;
import com.example.codecompare.rebuild.agent.migration.snapshot.BlockDecisionMutationService;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;
import com.example.codecompare.rebuild.stats.BlockStatsService;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import com.example.codecompare.rebuild.stats.DiffSegmentDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeBlockMigrationServiceTest {

    @TempDir
    Path workspace;

    @Test
    void applyAnnotatedCopiesInsertsDescendingWithinFile() throws Exception {
        Path targetRoot = workspace.resolve("targetProject");
        Files.createDirectories(targetRoot.resolve("src"));
        Path targetFile = targetRoot.resolve("src").resolve("Demo.java");
        Files.write(targetFile,
                Arrays.asList("header", "lineA", "lineB", "footer"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        ApplicationProperties properties = new ApplicationProperties();
        properties.getProject().setRoots(Collections.singletonList(targetRoot.toString()));
        ProjectRootRegistry registry = new ProjectRootRegistry(properties);

        BlockStatsService blockStatsService = mock(BlockStatsService.class);
        AnnotationRenderingService annotationRenderingService = mock(AnnotationRenderingService.class);
        when(annotationRenderingService.normalizeLineEndings(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BlockDecisionMutationService mutationService = new BlockDecisionMutationService(Clock.systemUTC());
        ProjectFileResolver projectFileResolver = new ProjectFileResolver(registry);
        AnnotatedFileWriter annotatedFileWriter = new AnnotatedFileWriter(projectFileResolver);

        DiffSynchronizationService diffSynchronizationService = mock(DiffSynchronizationService.class);
        when(diffSynchronizationService.refreshSiblingDiffs(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        CodeBlockDetailDTO block1 = new CodeBlockDetailDTO(
                "block-1",
                "cmp-1",
                null,
                targetRoot.getFileName().toString(),
                "src/Demo.java",
                2,
                2,
                null,
                "newLine1\nnewLine2\n",
                "pending",
                "pendingLabel",
                Collections.<String>emptyList(),
                false,
                null,
                null,
                null,
                Collections.<DiffSegmentDTO>emptyList());

        CodeBlockDetailDTO block2 = new CodeBlockDetailDTO(
                "block-2",
                "cmp-1",
                null,
                targetRoot.getFileName().toString(),
                "src/Demo.java",
                4,
                4,
                null,
                "// tail comment\n",
                "pending",
                "pendingLabel",
                Collections.<String>emptyList(),
                false,
                null,
                null,
                null,
                Collections.<DiffSegmentDTO>emptyList());

        when(blockStatsService.findDetail(any(), eq("block-1"))).thenReturn(block1);
        when(blockStatsService.findDetail(any(), eq("block-2"))).thenReturn(block2);

        BlockDecisionSnapshot initialSnapshot = BlockDecisionSnapshot.builder()
                .comparisonId("cmp-1")
                .filePath("src/Demo.java")
                .sourceProjectCode("source-project")
                .targetProjectCode(targetRoot.getFileName().toString())
                .records(Arrays.asList(
                        decisionRecord("block-1", 2),
                        decisionRecord("block-2", 6)))
                .build();

        InMemoryBlockDecisionRepository repository = new InMemoryBlockDecisionRepository(initialSnapshot);

        MigrationAnnotationProperties migrationAnnotationProperties = new MigrationAnnotationProperties();

        CodeBlockMigrationService service = new CodeBlockMigrationService(
                blockStatsService,
                repository,
                annotationRenderingService,
                mutationService,
                annotatedFileWriter,
                diffSynchronizationService,
                migrationAnnotationProperties);

        CodeBlockMigrationService.MigrationOperationResult result =
                service.applyAnnotatedCopies(Arrays.asList("block-1", "block-2"));

        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.hasFailures()).isFalse();

        List<String> finalLines = Files.readAllLines(targetFile, StandardCharsets.UTF_8);
        assertThat(finalLines).containsExactly(
                "header",
                "newLine1",
                "newLine2",
                "lineA",
                "lineB",
                "// tail comment",
                "footer");
    }

    @Test
    void applyAnnotatedCopiesSkipsInvalidBlocks() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getProject().setRoots(Collections.emptyList());
        ProjectRootRegistry registry = new ProjectRootRegistry(properties);

        BlockStatsService blockStatsService = mock(BlockStatsService.class);
        when(blockStatsService.findDetail(any(), eq("missing"))).thenReturn(null);

        AnnotationRenderingService annotationRenderingService = mock(AnnotationRenderingService.class);
        BlockDecisionMutationService mutationService = new BlockDecisionMutationService(Clock.systemUTC());
        ProjectFileResolver projectFileResolver = new ProjectFileResolver(registry);
        AnnotatedFileWriter annotatedFileWriter = new AnnotatedFileWriter(projectFileResolver);
        DiffSynchronizationService diffSynchronizationService = mock(DiffSynchronizationService.class);
        when(diffSynchronizationService.refreshSiblingDiffs(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        InMemoryBlockDecisionRepository repository = new InMemoryBlockDecisionRepository(null);
        MigrationAnnotationProperties migrationAnnotationProperties = new MigrationAnnotationProperties();

        CodeBlockMigrationService service = new CodeBlockMigrationService(
                blockStatsService,
                repository,
                annotationRenderingService,
                mutationService,
                annotatedFileWriter,
                diffSynchronizationService,
                migrationAnnotationProperties);

        CodeBlockMigrationService.MigrationOperationResult result =
                service.applyAnnotatedCopies(Collections.singletonList("missing"));

        assertThat(result.requested()).isEqualTo(1);
        assertThat(result.succeeded()).isZero();
        assertThat(result.hasFailures()).isTrue();
    }

    private static BlockDecisionRecord decisionRecord(String blockId, int targetStartLine) {
        BlockDiff diff = BlockDiff.builder()
                .targetStartLine(targetStartLine)
                .targetLines(Collections.singletonList("// placeholder"))
                .build();
        return BlockDecisionRecord.builder()
                .id(blockId)
                .comparisonId("cmp-1")
                .filePath("src/Demo.java")
                .status("pending")
                .riskLevel("pending")
                .metadata(new LinkedHashMap<>())
                .diff(diff)
                .build();
    }

    private static final class InMemoryBlockDecisionRepository implements BlockDecisionRepository {

        private BlockDecisionSnapshot snapshot;

        private InMemoryBlockDecisionRepository(BlockDecisionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public BlockDecisionSnapshot save(BlockDecisionSnapshot snapshot) {
            this.snapshot = snapshot;
            return snapshot;
        }

        @Override
        public Optional<BlockDecisionSnapshot> findLatest(String comparisonId, String filePath) {
            if (snapshot == null) {
                return Optional.empty();
            }
            if (snapshot.getComparisonId().equals(comparisonId)
                    && snapshot.getFilePath().equals(filePath)) {
                return Optional.of(snapshot);
            }
            return Optional.empty();
        }

        @Override
        public PageResult<BlockDecisionSnapshot> findHistory(String comparisonId, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<BlockDecisionSnapshot> findHistory(String comparisonId, String filePath, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String comparisonId, String filePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll(String comparisonId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void purgeOlderThan(String comparisonId, java.time.Instant threshold) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> listComparisonIds() {
            return Collections.emptySet();
        }
    }
}
