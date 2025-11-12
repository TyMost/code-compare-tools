package com.example.migratediff.application;

import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.migration.DecisionType;
import com.example.migratediff.domain.migration.MigrationResult;
import com.example.migratediff.domain.migration.MigrationSummary;
import com.example.migratediff.domain.migration.MigrationTask;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationAppServiceTest {

    @Mock
    private CoverageAppService coverageAppService;
    @Mock
    private DiffAppService diffAppService;

    private GenerateAppService generateAppService;
    private MigrationAppService migrationAppService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        generateAppService = new GenerateAppService();
        migrationAppService = new MigrationAppService(coverageAppService, diffAppService, generateAppService);
        lenient().when(coverageAppService.analyzeCoverage(any(String.class), any(DeltaGroup.class), eq(true)))
                .thenReturn(null);
    }

    @Test
    void preview_shouldProduceBlockResults() {
        DeltaGroup group = buildDeltaGroup();
        when(diffAppService.mergeDelta(org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class),
                org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class))).thenReturn(group);

        MigrationSummary summary = buildSummary();
        MigrationSummary previewed = migrationAppService.preview(summary);

        MigrationResult result = previewed.getResult();
        assertNotNull(result);
        assertFalse(result.getBlockResults().isEmpty());
        assertEquals(DecisionType.INSERT, result.getBlockResults().get(0).getDecisionType());
        assertTrue(result.getPreviewContent().contains("迁移生成的代码片段开始"));
    }

    @Test
    void apply_shouldReplaceTemplateInsteadOfAppending() throws Exception {
        DeltaGroup first = buildDeltaGroup("System.out.println(\"first\");");
        DeltaGroup second = buildDeltaGroup("System.out.println(\"second\");");
        when(diffAppService.mergeDelta(org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class),
                org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class))).thenReturn(first, second);

        Path target = tempDir.resolve("src/Main.java");
        Files.createDirectories(target.getParent());

        MigrationSummary firstSummary = buildSummary();
        migrationAppService.preview(firstSummary);
        migrationAppService.apply(firstSummary);

        String afterFirst = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertTrue(afterFirst.contains("System.out.println(\"first\");"));

        MigrationSummary secondSummary = buildSummary();
        migrationAppService.preview(secondSummary);
        migrationAppService.apply(secondSummary);

        String afterSecond = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertTrue(afterSecond.contains("System.out.println(\"second\");"));
        assertFalse(afterSecond.contains("System.out.println(\"first\");"));
        assertEquals(1, countOccurrences(afterSecond, "迁移生成的代码片段开始"));
    }

    @Test
    void apply_shouldAppendWhenMarkerMissing() throws Exception {
        DeltaGroup group = buildDeltaGroup("System.out.println(\"first\");");
        when(diffAppService.mergeDelta(org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class),
                org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class))).thenReturn(group);

        Path target = tempDir.resolve("src/Main.java");
        Files.createDirectories(target.getParent());
        Files.write(target, Collections.singletonList("// original code"), StandardCharsets.UTF_8);

        MigrationSummary summary = buildSummary();
        migrationAppService.preview(summary);
        migrationAppService.apply(summary);

        String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertTrue(content.contains("// original code"));
        assertTrue(content.contains("System.out.println(\"first\");"));
        verify(coverageAppService).analyzeCoverage(any(String.class), eq(group), eq(true));
    }

    @Test
    void apply_shouldUseRecordedLineNumbersForUpdate() throws Exception {
        DeltaGroup group = buildUpdateGroup("        System.out.println(\"oracle\");",
                "        System.out.println(\"gauss\");", 3, 3);
        when(diffAppService.mergeDelta(org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class),
                org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class))).thenReturn(group);

        Path target = tempDir.resolve("src/Main.java");
        Files.createDirectories(target.getParent());
        List<String> original = Arrays.asList(
                "public class Main {",
                "    public void test() {",
                "        System.out.println(\"gauss\");",
                "    }",
                "}"
        );
        Files.write(target, original, StandardCharsets.UTF_8);

        MigrationSummary summary = buildSummary();
        migrationAppService.preview(summary);
        migrationAppService.apply(summary);

        String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertTrue(content.contains("    public void test() {" + System.lineSeparator() + "/** 迁移适配开"));
        assertEquals(1, countOccurrences(content, "/** 迁移适配开"));
    }

    @Test
    void apply_shouldLocateSnippetAfterLineShift() throws Exception {
        DeltaGroup group = buildUpdateGroup("        System.out.println(\"oracle\");",
                "        System.out.println(\"gauss\");", 3, 3);
        when(diffAppService.mergeDelta(org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class),
                org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class))).thenReturn(group);

        Path target = tempDir.resolve("src/Main.java");
        Files.createDirectories(target.getParent());
        List<String> shifted = Arrays.asList(
                "public class Main {",
                "// 新增注释导致行号漂移",
                "    public void test() {",
                "        System.out.println(\"gauss\");",
                "    }",
                "}"
        );
        Files.write(target, shifted, StandardCharsets.UTF_8);

        MigrationSummary summary = buildSummary();
        migrationAppService.preview(summary);
        migrationAppService.apply(summary);

        String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertTrue(content.contains("// 新增注释导致行号漂移"));
        assertEquals(1, countOccurrences(content, "/** 迁移适配开"));
    }

    @Test
    void revert_shouldRestoreBackup() throws Exception {
        DeltaGroup group = buildDeltaGroup("System.out.println(\"first\");");
        when(diffAppService.mergeDelta(org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class),
                org.mockito.ArgumentMatchers.<DiffSummary>nullable(DiffSummary.class))).thenReturn(group);

        Path target = tempDir.resolve("src/Main.java");
        Files.createDirectories(target.getParent());
        Files.write(target, Collections.singletonList("// origin"), StandardCharsets.UTF_8);

        MigrationSummary summary = buildSummary();
        migrationAppService.preview(summary);
        migrationAppService.apply(summary);

        MigrationTask task = summary.getTask();
        MigrationResult reverted = migrationAppService.revert(task);

        assertTrue(reverted.isSuccess());
        String afterRevert = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        assertEquals("// origin" + System.lineSeparator(), afterRevert);
        assertFalse(Files.exists(target.resolveSibling("Main.java.migrationBak")));
    }

    private MigrationSummary buildSummary() {
        MigrationSummary summary = new MigrationSummary();
        RepoConfig repoConfig = RepoConfig.builder()
                .repoPath(RepoPath.builder()
                        .absolutePath(tempDir.toString())
                        .build())
                .build();
        DiffSummary deltaSummary = DiffSummary.builder()
                .repoConfig(repoConfig)
                .build();
        summary.setDeltaGSummary(deltaSummary);
        summary.setDeltaOSummary(deltaSummary);
        summary.setTask(new MigrationTask());
        return summary;
    }

    private DeltaGroup buildDeltaGroup() {
        return buildInsertGroup("System.out.println(\"source\");", 1);
    }

    private DeltaGroup buildDeltaGroup(String sourceContent) {
        return buildInsertGroup(sourceContent, 1);
    }

    private DeltaGroup buildInsertGroup(String sourceContent, int startLine) {
        DiffBlock oracleBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo(sourceContent)
                .startLineFrom(startLine)
                .endLineFrom(startLine)
                .startLineTo(startLine)
                .endLineTo(startLine)
                .build();
        return buildGroupWithBlocks(oracleBlock, null);
    }

    private DeltaGroup buildGroupWithBlocks(DiffBlock oracleBlock, DiffBlock gaussBlock) {
        DiffFile deltaO = DiffFile.builder()
                .relativePath("src/Main.java")
                .blocks(oracleBlock == null ? Collections.emptyList() : Collections.singletonList(oracleBlock))
                .build();
        DiffFile deltaG = DiffFile.builder()
                .relativePath("src/Main.java")
                .blocks(gaussBlock == null ? Collections.emptyList() : Collections.singletonList(gaussBlock))
                .build();
        return DeltaGroup.builder()
                .deltaO(deltaO)
                .deltaG(deltaG)
                .build();
    }

    private DeltaGroup buildUpdateGroup(String oracleContent, String gaussContent, int startLine, int endLine) {
        DiffBlock oracleBlock = DiffBlock.builder()
                .contentFrom(oracleContent)
                .contentTo(oracleContent)
                .startLineFrom(startLine)
                .endLineFrom(endLine)
                .startLineTo(startLine)
                .endLineTo(endLine)
                .build();
        DiffBlock gaussBlock = DiffBlock.builder()
                .contentFrom(gaussContent)
                .contentTo(gaussContent)
                .startLineFrom(startLine)
                .endLineFrom(endLine)
                .startLineTo(startLine)
                .endLineTo(endLine)
                .build();
        return buildGroupWithBlocks(oracleBlock, gaussBlock);
    }

    private int countOccurrences(String text, String marker) {
        int count = 0;
        int index = text.indexOf(marker);
        while (index >= 0) {
            count++;
            index = text.indexOf(marker, index + marker.length());
        }
        return count;
    }
}
