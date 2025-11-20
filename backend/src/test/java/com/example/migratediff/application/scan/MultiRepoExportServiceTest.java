package com.example.migratediff.application.scan;

import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.diff.DiffType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MultiRepoExportServiceTest {

    private MultiRepoExportService exportService;
    private ScanResultStore scanResultStore;

    @BeforeEach
    void setUp() {
        scanResultStore = new ScanResultStore();
        exportService = new MultiRepoExportService(scanResultStore, new DiffMatrixAssembler(), new DiffMatrixFilter());
    }

    @Test
    void shouldAggregateSingleRepo() {
        ScanReport report = sampleReport("task-1", "preset-a", 0.8D);
        scanResultStore.save(report);

        MultiRepoExportRequest request = MultiRepoExportRequest.builder()
                .format("csv")
                .filterCriteria(DiffMatrixFilterCriteria.builder().includeEmptyCoverage(true).build())
                .repos(Collections.singletonList(
                        MultiRepoExportRequest.RepoSelection.builder()
                                .taskId("task-1")
                                .alias("Repo-A")
                                .build()))
                .build();

        MultiRepoExportResult result = exportService.export(request);

        assertEquals(1, result.getRepoReports().size());
        MultiRepoExportResult.RepoReport repoReport = result.getRepoReports().get(0);
        assertEquals("Repo-A", repoReport.getDisplayName());
        assertEquals(1, repoReport.getRows().size());
        assertNotNull(repoReport.getStats());
        assertEquals(1, repoReport.getStats().getTotalFiles());
    }

    private ScanReport sampleReport(String taskId, String presetName, double coverage) {
        DiffBlock oracleBlock = DiffBlock.builder()
                .startLineFrom(1)
                .endLineFrom(5)
                .startLineTo(1)
                .endLineTo(5)
                .type(DiffType.ADD)
                .build();
        DiffFile oracleFile = DiffFile.builder()
                .relativePath("src/Main.java")
                .blocks(Collections.singletonList(oracleBlock))
                .build();
        DiffFile gaussFile = DiffFile.builder()
                .relativePath("src/Main.java")
                .blocks(Collections.singletonList(oracleBlock))
                .build();
        DiffSummary oracle = DiffSummary.builder()
                .diffFiles(Collections.singletonList(oracleFile))
                .build();
        DiffSummary gauss = DiffSummary.builder()
                .diffFiles(Collections.singletonList(gaussFile))
                .build();
        CoverageDetail coverageDetail = CoverageDetail.builder()
                .filePath("src/Main.java")
                .coverage(coverage)
                .build();
        CoverageSummary coverageSummary = CoverageSummary.builder()
                .overallCoverage(coverage)
                .details(Collections.singletonList(coverageDetail))
                .build();
        return new ScanReport(
                taskId,
                ScanMode.FULL,
                presetName,
                "repo-" + taskId,
                "Repo-" + taskId,
                true,
                oracle,
                gauss,
                coverageSummary);
    }
}
