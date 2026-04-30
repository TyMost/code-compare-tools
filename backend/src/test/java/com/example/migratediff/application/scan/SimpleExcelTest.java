package com.example.migratediff.application.scan;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 简化的Excel测试，不依赖Spring Boot
 */
public class SimpleExcelTest {

    public static void main(String[] args) {
        try {
            SimpleExcelTest test = new SimpleExcelTest();
            test.testGenerateExcelFile();
            System.out.println("Excel文件生成完成！请查看 output 目录");
        } catch (Exception e) {
            System.err.println("生成Excel文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void testGenerateExcelFile() throws IOException {
        // 创建测试数据
        MultiRepoExportResult testResult = createTestData();
        
        // 创建Excel写入器
        ExcelMultiRepoReportWriter writer = new ExcelMultiRepoReportWriter();
        
        // 生成Excel文件
        byte[] excelData = writer.write(testResult);
        
        // 保存到文件系统供查看
        Path outputPath = Paths.get("output/test-export-" + System.currentTimeMillis() + ".xlsx");
        Files.createDirectories(outputPath.getParent());
        
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            fos.write(excelData);
        }
        
        System.out.println("Excel文件已生成: " + outputPath.toAbsolutePath());
        System.out.println("文件大小: " + excelData.length + " bytes");
    }

    /**
     * 创建测试数据
     */
    private MultiRepoExportResult createTestData() {
        // 创建文件详细提交列表
        List<FileCommitDetail> fileCommitDetails = createFileCommitDetails();
        
        // 创建仓库报告
        List<MultiRepoExportResult.RepoReport> repoReports = createRepoReports();
        
        return MultiRepoExportResult.builder()
                .generatedAt(Instant.now())
                .filterCriteria(createFilterCriteria())
                .repoReports(repoReports)
                .allFileCommitDetails(fileCommitDetails)
                .allAuthorStats(new ArrayList<>())
                .build();
    }

    /**
     * 创建文件详细提交数据
     */
    private List<FileCommitDetail> createFileCommitDetails() {
        List<FileCommitDetail> details = new ArrayList<>();
        
        // Oracle仓库的提交
        details.add(FileCommitDetail.builder()
                .filePath("src/main/java/com/example/service/UserService.java")
                .repoSource("Oracle")
                .commitHash("a1b2c3d4e5f6")
                .authorName("张三")
                .authorEmail("zhangsan@example.com")
                .commitTime(Instant.parse("2025-11-15T10:30:00Z"))
                .commitMessage("feat: 添加用户登录功能")
                .commitType("feature")
                .changeType("MODIFY")
                .linesAdded(25)
                .linesRemoved(5)
                .inTimeRange(true)
                .build());
        
        details.add(FileCommitDetail.builder()
                .filePath("src/main/java/com/example/service/UserService.java")
                .repoSource("Oracle")
                .commitHash("f6e5d4c3b2a1")
                .authorName("李四")
                .authorEmail("lisi@example.com")
                .commitTime(Instant.parse("2025-11-20T14:15:00Z"))
                .commitMessage("fix: 修复登录验证逻辑")
                .commitType("bugfix")
                .changeType("MODIFY")
                .linesAdded(8)
                .linesRemoved(12)
                .inTimeRange(true)
                .build());
        
        // Gauss仓库的提交
        details.add(FileCommitDetail.builder()
                .filePath("src/main/java/com/example/service/UserService.java")
                .repoSource("Gauss")
                .commitHash("b2c3d4e5f6a1")
                .authorName("王五")
                .authorEmail("wangwu@example.com")
                .commitTime(Instant.parse("2025-11-18T09:45:00Z"))
                .commitMessage("refactor: 重构用户服务架构")
                .commitType("refactor")
                .changeType("MODIFY")
                .linesAdded(45)
                .linesRemoved(30)
                .inTimeRange(true)
                .build());
        
        return details;
    }

    /**
     * 创建仓库报告数据
     */
    private List<MultiRepoExportResult.RepoReport> createRepoReports() {
        List<MultiRepoExportResult.RepoReport> reports = new ArrayList<>();
        
        // Oracle仓库报告
        List<DiffMatrixRow> oracleRows = createOracleRows();
        MultiRepoExportResult.RepoStats oracleStats = createOracleStats();
        
        reports.add(MultiRepoExportResult.RepoReport.builder()
                .displayName("Oracle主仓库")
                .scanReport(createScanReport("oracle-task-001", "Oracle"))
                .rows(oracleRows)
                .stats(oracleStats)
                .authorStats(new ArrayList<>())
                .build());
        
        // Gauss仓库报告
        List<DiffMatrixRow> gaussRows = createGaussRows();
        MultiRepoExportResult.RepoStats gaussStats = createGaussStats();
        
        reports.add(MultiRepoExportResult.RepoReport.builder()
                .displayName("Gauss迁移仓库")
                .scanReport(createScanReport("gauss-task-002", "Gauss"))
                .rows(gaussRows)
                .stats(gaussStats)
                .authorStats(new ArrayList<>())
                .build());
        
        return reports;
    }

    /**
     * 创建Oracle仓库的文件行数据
     */
    private List<DiffMatrixRow> createOracleRows() {
        List<DiffMatrixRow> rows = new ArrayList<>();
        
        rows.add(DiffMatrixRow.builder()
                .filePath("src/main/java/com/example/service/UserService.java")
                .coverage(0.85)
                .status("matched")
                .oracleAdded(33)
                .oracleRemoved(17)
                .gaussAdded(0)
                .gaussRemoved(0)
                .lastOracleAuthor("张三")
                .lastOracleCommitTime(Instant.parse("2025-11-22T16:20:00Z"))
                .lastOracleCommitHash("c3d4e5f6a1b2")
                .lastOracleCommitMessage("feat: 新增用户信息字段")
                .oracleCommitCount(2)
                .hasOracleCommits(true)
                .gaussCommitCount(0)
                .hasGaussCommits(false)
                .build());
        
        rows.add(DiffMatrixRow.builder()
                .filePath("src/main/java/com/example/dto/UserDTO.java")
                .coverage(0.92)
                .status("matched")
                .oracleAdded(15)
                .oracleRemoved(8)
                .gaussAdded(0)
                .gaussRemoved(0)
                .lastOracleAuthor("张三")
                .lastOracleCommitTime(Instant.parse("2025-11-22T16:20:00Z"))
                .lastOracleCommitHash("c3d4e5f6a1b2")
                .lastOracleCommitMessage("feat: 新增用户信息字段")
                .oracleCommitCount(1)
                .hasOracleCommits(true)
                .gaussCommitCount(0)
                .hasGaussCommits(false)
                .build());
        
        return rows;
    }

    /**
     * 创建Gauss仓库的文件行数据
     */
    private List<DiffMatrixRow> createGaussRows() {
        List<DiffMatrixRow> rows = new ArrayList<>();
        
        rows.add(DiffMatrixRow.builder()
                .filePath("src/main/java/com/example/service/UserService.java")
                .coverage(0.85)
                .status("matched")
                .oracleAdded(0)
                .oracleRemoved(0)
                .gaussAdded(45)
                .gaussRemoved(30)
                .lastGaussAuthor("王五")
                .lastGaussCommitTime(Instant.parse("2025-11-18T09:45:00Z"))
                .lastGaussCommitHash("b2c3d4e5f6a1")
                .lastGaussCommitMessage("refactor: 重构用户服务架构")
                .oracleCommitCount(0)
                .hasOracleCommits(false)
                .gaussCommitCount(1)
                .hasGaussCommits(true)
                .build());
        
        rows.add(DiffMatrixRow.builder()
                .filePath("src/main/resources/application.yml")
                .coverage(0.78)
                .status("gauss-only")
                .oracleAdded(0)
                .oracleRemoved(0)
                .gaussAdded(5)
                .gaussRemoved(2)
                .lastGaussAuthor("赵六")
                .lastGaussCommitTime(Instant.parse("2025-11-25T11:00:00Z"))
                .lastGaussCommitHash("d4e5f6a1b2c3")
                .lastGaussCommitMessage("config: 更新数据库连接配置")
                .oracleCommitCount(0)
                .hasOracleCommits(false)
                .gaussCommitCount(1)
                .hasGaussCommits(true)
                .build());
        
        return rows;
    }

    /**
     * 创建Oracle仓库统计信息
     */
    private MultiRepoExportResult.RepoStats createOracleStats() {
        return MultiRepoExportResult.RepoStats.builder()
                .totalFiles(2)
                .matched(2)
                .oracleOnly(0)
                .gaussOnly(0)
                .overallCoverage(0.88)
                .build();
    }

    /**
     * 创建Gauss仓库统计信息
     */
    private MultiRepoExportResult.RepoStats createGaussStats() {
        return MultiRepoExportResult.RepoStats.builder()
                .totalFiles(2)
                .matched(1)
                .oracleOnly(0)
                .gaussOnly(1)
                .overallCoverage(0.82)
                .build();
    }

    /**
     * 创建扫描报告
     */
    private ScanReport createScanReport(String taskId, String presetName) {
        return new ScanReport(
                taskId,
                ScanMode.FULL,
                presetName,
                "test-repo-id",
                "test-repo-name",
                false,
                null, // oracleSummary
                null, // gaussSummary  
                null, // coverageSummary
                Instant.now()
        );
    }

    /**
     * 创建筛选条件
     */
    private DiffMatrixFilterCriteria createFilterCriteria() {
        return DiffMatrixFilterCriteria.builder()
                .statuses(Arrays.asList("matched", "oracle-only", "gauss-only"))
                .coverageMin(0.0)
                .coverageMax(1.0)
                .includeEmptyCoverage(false)
                .includeCommitInfo(true)
                .build();
    }
}
