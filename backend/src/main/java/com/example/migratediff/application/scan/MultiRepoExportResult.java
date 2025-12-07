package com.example.migratediff.application.scan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * 聚合导出结果，包含每个仓库的矩阵和汇总。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiRepoExportResult {
    private Instant generatedAt;
    private DiffMatrixFilterCriteria filterCriteria;
    private List<RepoReport> repoReports;
    private List<CommitAuthorStats> allAuthorStats;
    private List<FileCommitDetail> allFileCommitDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepoReport {
        private String displayName;
        private ScanReport scanReport;
        private List<DiffMatrixRow> rows;
        private RepoStats stats;
        private List<CommitAuthorStats> authorStats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepoStats {
        private int totalFiles;
        private int matched;
        private int oracleOnly;
        private int gaussOnly;
        private double overallCoverage;
    }
}
