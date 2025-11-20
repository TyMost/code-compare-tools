package com.example.migratediff.application.scan;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * 聚合导出结果，包含每个仓库的矩阵和汇总。
 */
@Value
@Builder
public class MultiRepoExportResult {
    Instant generatedAt;
    DiffMatrixFilterCriteria filterCriteria;
    List<RepoReport> repoReports;

    @Value
    @Builder
    public static class RepoReport {
        String displayName;
        ScanReport scanReport;
        List<DiffMatrixRow> rows;
        RepoStats stats;
    }

    @Value
    @Builder
    public static class RepoStats {
        int totalFiles;
        int matched;
        int oracleOnly;
        int gaussOnly;
        double overallCoverage;
    }
}
