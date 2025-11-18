package com.example.migratediff.application.scan;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import com.example.migratediff.domain.repo.RepoType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanReportTest {

    @TempDir
    Path tempDir;

    @Test
    void overviewForGaussFile_shouldKeepRepoConfigWhenFileMissing() {
        Path oracleRoot = tempDir.resolve("oracle");
        Path gaussRoot = tempDir.resolve("gauss");
        DiffSummary oracleSummary = DiffSummary.builder()
                .repoConfig(repoConfig(oracleRoot, RepoType.ORACLE))
                .diffFiles(Collections.singletonList(DiffFile.builder()
                        .relativePath("src/Main.java")
                        .build()))
                .build();
        DiffSummary gaussSummary = DiffSummary.builder()
                .repoConfig(repoConfig(gaussRoot, RepoType.GAUSS))
                .diffFiles(Collections.emptyList())
                .build();
        ScanReport report = new ScanReport("task", ScanMode.FULL, oracleSummary, gaussSummary, null);

        DiffSummary gaussOverview = report.overviewForGaussFile("src/Main.java");

        assertNotNull(gaussOverview, "缺少 Gauss diff 时也应返回摘要，保留仓库根目录");
        assertEquals(gaussRoot.toString(),
                gaussOverview.getRepoConfig().getRepoPath().getAbsolutePath());
        assertTrue(gaussOverview.getDiffFiles().isEmpty(), "Gauss diff 不存在时应返回空列表");
    }

    private RepoConfig repoConfig(Path root, RepoType type) {
        return RepoConfig.builder()
                .repoPath(RepoPath.builder()
                        .absolutePath(root.toString())
                        .type(type)
                        .build())
                .build();
    }
}
