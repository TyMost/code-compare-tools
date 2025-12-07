package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.FileCommitHistoryDTO;
import com.example.migratediff.api.dto.GitCommitInfoDTO;
import com.example.migratediff.application.commit.GitCommitHistoryService;
import com.example.migratediff.config.MigrationDiffProperties;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 负责将多个任务的扫描结果聚合为导出视图。
 * 仓库级别时间筛选：每个仓库根据自己的时间配置筛选文件和提交信息
 */
@Service
@Slf4j
public class MultiRepoExportService {

    private final ScanResultStore scanResultStore;
    private final DiffMatrixAssembler diffMatrixAssembler;
    private final DiffMatrixFilter diffMatrixFilter;
    private final CommitInfoAggregatorService commitInfoAggregatorService;
    private final GitCommitHistoryService gitCommitHistoryService;
    private final MigrationDiffProperties migrationDiffProperties;

    public MultiRepoExportService(ScanResultStore scanResultStore,
                                  DiffMatrixAssembler diffMatrixAssembler,
                                  DiffMatrixFilter diffMatrixFilter,
                                  CommitInfoAggregatorService commitInfoAggregatorService,
                                  GitCommitHistoryService gitCommitHistoryService,
                                  MigrationDiffProperties migrationDiffProperties) {
        this.scanResultStore = scanResultStore;
        this.diffMatrixAssembler = diffMatrixAssembler;
        this.diffMatrixFilter = diffMatrixFilter;
        this.commitInfoAggregatorService = commitInfoAggregatorService;
        this.gitCommitHistoryService = gitCommitHistoryService;
        this.migrationDiffProperties = migrationDiffProperties;
    }

    public MultiRepoExportResult export(MultiRepoExportRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getRepos())) {
            throw new IllegalArgumentException("缺少有效的仓库列表");
        }
        DiffMatrixFilterCriteria criteria = buildCriteria(request);
        List<MultiRepoExportResult.RepoReport> repoReports = new ArrayList<>();
        List<CommitAuthorStats> allAuthorStats = new ArrayList<>();
        
        log.info("开始导出报表，仓库数量: {}, 包含提交信息: {}", 
            request.getRepos().size(), 
            criteria != null && criteria.isIncludeCommitInfo());
        
        for (MultiRepoExportRequest.RepoSelection selection : request.getRepos()) {
            ScanReport report = resolveReport(selection);
            List<DiffMatrixRow> rows = diffMatrixFilter.filter(
                    diffMatrixAssembler.assemble(report),
                    criteria);
            
            // 丰富提交信息 - 支持开关控制
            rows = enrichRowsWithCommitInfo(rows, report, criteria);
            
            MultiRepoExportResult.RepoStats stats = buildStats(report, rows);
            
            // 聚合提交者统计 - 支持开关控制，使用仓库级别时间筛选
            List<CommitAuthorStats> authorStats = aggregateAuthorStats(rows, report, criteria);
            allAuthorStats.addAll(authorStats);
            
            repoReports.add(MultiRepoExportResult.RepoReport.builder()
                    .displayName(resolveDisplayName(selection, report))
                    .scanReport(report)
                    .rows(rows)
                    .stats(stats)
                    .authorStats(authorStats)
                    .build());
        }
        
        // 获取所有文件的详细提交历史 - 支持开关控制和并行处理，使用仓库级别时间筛选
        List<FileCommitDetail> allFileCommitDetails = aggregateFileCommitDetails(repoReports, criteria);
        
        return MultiRepoExportResult.builder()
                .generatedAt(Instant.now())
                .filterCriteria(criteria)
                .repoReports(repoReports)
                .allAuthorStats(allAuthorStats)
                .allFileCommitDetails(allFileCommitDetails)
                .build();
    }

    /**
     * 为行数据丰富提交信息 - 支持开关控制
     */
    private List<DiffMatrixRow> enrichRowsWithCommitInfo(List<DiffMatrixRow> rows, ScanReport report, DiffMatrixFilterCriteria criteria) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        
        // 检查是否需要包含提交信息
        if (criteria != null && !criteria.isIncludeCommitInfo()) {
            log.info("跳过提交信息获取，根据用户设置");
            return rows;
        }
        
        // 获取仓库配置
        RepoConfig oracleRepo = extractRepoConfig(report, true);
        RepoConfig gaussRepo = extractRepoConfig(report, false);
        
        return rows.stream()
                .map(row -> commitInfoAggregatorService.enrichWithCommitInfo(row, oracleRepo, gaussRepo))
                .collect(Collectors.toList());
    }

    /**
     * 提取仓库配置
     */
    private RepoConfig extractRepoConfig(ScanReport report, boolean isOracle) {
        if (isOracle && report.getOracleSummary() != null) {
            return report.getOracleSummary().getRepoConfig();
        } else if (!isOracle && report.getGaussSummary() != null) {
            return report.getGaussSummary().getRepoConfig();
        }
        return null;
    }

    /**
     * 聚合提交者统计信息 - 支持开关控制，使用仓库级别时间筛选
     */
    private List<CommitAuthorStats> aggregateAuthorStats(List<DiffMatrixRow> rows, ScanReport report, DiffMatrixFilterCriteria criteria) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 检查是否需要包含提交信息
        if (criteria != null && !criteria.isIncludeCommitInfo()) {
            log.info("跳过提交者统计，根据用户设置");
            return new ArrayList<>();
        }
        
        RepoConfig oracleRepo = extractRepoConfig(report, true);
        RepoConfig gaussRepo = extractRepoConfig(report, false);
        String repoName = report.getPresetName() != null ? report.getPresetName() : report.getTaskId();
        
        // 使用仓库级别的时间范围，根据预设名称获取对应的时间配置
        Instant timeFrom = extractTimeFromByPreset(report.getPresetName());
        Instant timeTo = extractTimeToByPreset(report.getPresetName());
        
        return commitInfoAggregatorService.aggregateAuthorStats(rows, oracleRepo, gaussRepo, repoName, timeFrom, timeTo);
    }

    /**
     * 根据预设名称从application配置中提取时间开始
     */
    private Instant extractTimeFromByPreset(String presetName) {
        Instant timeFrom = migrationDiffProperties.getEarliestTimeFromByPreset(presetName);
        if (timeFrom != null) {
            log.info("从预设 {} 的配置文件读取时间开始: {}", presetName, timeFrom);
            return timeFrom;
        }
        
        // 如果预设配置中没有找到，使用全局默认值
        log.warn("预设 {} 的配置文件中未找到时间范围配置，使用全局默认值", presetName);
        return migrationDiffProperties.getEarliestTimeFrom() != null 
            ? migrationDiffProperties.getEarliestTimeFrom()
            : Instant.parse("2025-11-01T00:00:00Z");
    }

    /**
     * 根据预设名称从application配置中提取时间结束
     */
    private Instant extractTimeToByPreset(String presetName) {
        Instant timeTo = migrationDiffProperties.getLatestTimeToByPreset(presetName);
        if (timeTo != null) {
            log.info("从预设 {} 的配置文件读取时间结束: {}", presetName, timeTo);
            return timeTo;
        }
        
        // 如果预设配置中没有找到，使用全局默认值
        log.warn("预设 {} 的配置文件中未找到时间范围配置，使用全局默认值", presetName);
        return migrationDiffProperties.getLatestTimeTo() != null
            ? migrationDiffProperties.getLatestTimeTo()
            : Instant.parse("2025-12-02T23:59:59Z");
    }

    private DiffMatrixFilterCriteria buildCriteria(MultiRepoExportRequest request) {
        DiffMatrixFilterCriteria criteria = request.getFilterCriteria();
        if (criteria != null) {
            return criteria;
        }
        return DiffMatrixFilterCriteria.builder()
                .statuses(null)
                .coverageMin(null)
                .coverageMax(null)
                .includeEmptyCoverage(true)
                .includeCommitInfo(false) // 默认不包含提交信息
                .build();
    }

    private ScanReport resolveReport(MultiRepoExportRequest.RepoSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("仓库配置为空");
        }
        
        log.debug("解析仓库选择: taskId={}, presetName={}, alias={}", 
            selection.getTaskId(), selection.getPresetName(), selection.getAlias());
        
        if (StringUtils.hasText(selection.getTaskId())) {
            log.debug("通过taskId查找扫描结果: {}", selection.getTaskId());
            return scanResultStore.find(selection.getTaskId())
                    .orElseThrow(() -> {
                        log.warn("找不到taskId对应的扫描结果: {}", selection.getTaskId());
                        return new NotFoundException("找不到 taskId=" + selection.getTaskId() + " 对应的扫描结果");
                    });
        }
        
        if (StringUtils.hasText(selection.getPresetName())) {
            log.debug("通过presetName查找扫描结果: {}", selection.getPresetName());
            return scanResultStore.findLatestByPreset(selection.getPresetName())
                    .orElseThrow(() -> {
                        log.warn("找不到presetName对应的扫描结果: {}", selection.getPresetName());
                        return new NotFoundException("预设 " + selection.getPresetName() + " 暂无扫描记录");
                    });
        }
        
        log.error("仓库配置缺少taskId和presetName: {}", selection);
        throw new IllegalArgumentException("仓库配置必须提供 taskId 或 presetName");
    }

    private MultiRepoExportResult.RepoStats buildStats(ScanReport report, List<DiffMatrixRow> rows) {
        int total = rows.size();
        int matched = (int) rows.stream().filter(row -> "matched".equals(row.getStatus())).count();
        int oracleOnly = (int) rows.stream().filter(row -> "oracle-only".equals(row.getStatus())).count();
        int gaussOnly = (int) rows.stream().filter(row -> "gauss-only".equals(row.getStatus())).count();
        double overallCoverage = report.getCoverageSummary() == null
                ? 0D
                : round(report.getCoverageSummary().getOverallCoverage());
        return MultiRepoExportResult.RepoStats.builder()
                .totalFiles(total)
                .matched(matched)
                .oracleOnly(oracleOnly)
                .gaussOnly(gaussOnly)
                .overallCoverage(overallCoverage)
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    /**
     * 聚合文件详细提交历史 - 支持开关控制和并行处理，使用仓库级别时间筛选
     */
    private List<FileCommitDetail> aggregateFileCommitDetails(List<MultiRepoExportResult.RepoReport> repoReports, DiffMatrixFilterCriteria criteria) {
        // 检查是否需要包含提交信息
        if (criteria != null && !criteria.isIncludeCommitInfo()) {
            log.info("跳过文件提交历史聚合，根据用户设置");
            return new ArrayList<>();
        }
        
        List<FileCommitDetail> allDetails = new ArrayList<>();
        
        log.info("开始聚合文件提交历史，仓库数量: {}, 总文件数: {}", 
            repoReports.size(), 
            repoReports.stream().mapToInt(repo -> repo.getRows().size()).sum());
        
        // 使用并行处理获取提交历史，每个仓库使用自己的时间范围
        List<CompletableFuture<List<FileCommitDetail>>> futures = new ArrayList<>();
        
        for (MultiRepoExportResult.RepoReport repo : repoReports) {
            ScanReport report = repo.getScanReport();
            RepoConfig oracleRepo = extractRepoConfig(report, true);
            RepoConfig gaussRepo = extractRepoConfig(report, false);
            
            // 获取该仓库的时间范围
            Instant timeFrom = extractTimeFromByPreset(report.getPresetName());
            Instant timeTo = extractTimeToByPreset(report.getPresetName());
            
            log.debug("处理仓库报告: 预设={}, oracleRepo={}, gaussRepo={}, 时间范围: {} 至 {}", 
                report.getPresetName(),
                oracleRepo != null ? oracleRepo.getRepoPath().getAbsolutePath() : "null",
                gaussRepo != null ? gaussRepo.getRepoPath().getAbsolutePath() : "null",
                timeFrom, timeTo);
            
            // 并行获取Oracle仓库的提交历史
            if (oracleRepo != null) {
                List<String> oracleFilePaths = repo.getRows().stream()
                    .filter(row -> row.getHasOracleCommits())
                    .map(DiffMatrixRow::getFilePath)
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!oracleFilePaths.isEmpty()) {
                    CompletableFuture<List<FileCommitDetail>> oracleFuture = 
                        getBatchFileCommitDetailsAsync(oracleFilePaths, "Oracle", oracleRepo, timeFrom, timeTo);
                    futures.add(oracleFuture);
                }
            }
            
            // 并行获取Gauss仓库的提交历史
            if (gaussRepo != null) {
                List<String> gaussFilePaths = repo.getRows().stream()
                    .filter(row -> row.getHasGaussCommits())
                    .map(DiffMatrixRow::getFilePath)
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!gaussFilePaths.isEmpty()) {
                    CompletableFuture<List<FileCommitDetail>> gaussFuture = 
                        getBatchFileCommitDetailsAsync(gaussFilePaths, "Gauss", gaussRepo, timeFrom, timeTo);
                    futures.add(gaussFuture);
                }
            }
        }
        
        // 等待所有并行任务完成
        for (CompletableFuture<List<FileCommitDetail>> future : futures) {
            try {
                List<FileCommitDetail> details = future.get();
                allDetails.addAll(details);
                log.debug("并行任务完成，获取到 {} 个提交", details.size());
            } catch (Exception e) {
                log.error("并行获取提交历史失败", e);
            }
        }
        
        // 按时间倒序排序
        List<FileCommitDetail> sortedDetails = allDetails.stream()
                .sorted((a, b) -> b.getCommitTime().compareTo(a.getCommitTime()))
                .collect(Collectors.toList());
        
        log.info("文件提交历史聚合完成: 总计 {} 个提交，使用了 {} 个并行任务", 
            sortedDetails.size(), futures.size());
        return sortedDetails;
    }
    
    /**
     * 异步批量获取多个文件的详细提交历史
     */
    @Async("commitInfoExecutor")
    public CompletableFuture<List<FileCommitDetail>> getBatchFileCommitDetailsAsync(
            List<String> filePaths, String repoSource, 
            RepoConfig repoConfig, 
            Instant timeFrom, 
            Instant timeTo) {
        
        List<FileCommitDetail> allDetails = new ArrayList<>();
        
        if (repoConfig == null || CollectionUtils.isEmpty(filePaths)) {
            log.debug("Repository config is null or file list is empty for repo source: {}", repoSource);
            return CompletableFuture.completedFuture(allDetails);
        }
        
        log.info("开始异步批量获取{}仓库的提交历史: {} 个文件，时间范围: {} 至 {}", 
            repoSource, filePaths.size(), timeFrom, timeTo);
        long startTime = System.currentTimeMillis();
        
        try {
            // 分批处理，避免单次处理过多文件
            int batchSize = 30; // 减少批次大小以提高并行效率
            for (int i = 0; i < filePaths.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, filePaths.size());
                List<String> batch = filePaths.subList(i, endIndex);
                
                log.debug("处理批次 {}/{}: {} 个文件", (i / batchSize + 1), 
                    (filePaths.size() + batchSize - 1) / batchSize, batch.size());
                
                for (String filePath : batch) {
                    try {
                        List<FileCommitDetail> fileDetails = getFileCommitDetails(
                            filePath, repoSource, repoConfig, timeFrom, timeTo);
                        allDetails.addAll(fileDetails);
                    } catch (Exception e) {
                        log.warn("批量处理中获取文件提交历史失败: {}, 文件: {}", e.getMessage(), filePath);
                    }
                }
                
                // 短暂缓避免过快连续处理
                if (endIndex < filePaths.size()) {
                    try {
                        Thread.sleep(5); // 减少暂停时间
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("异步批量获取{}仓库提交历史失败: {}", repoSource, e.getMessage(), e);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("{}仓库异步批量获取完成: {} 个文件, {} 个提交, 耗时: {}ms", 
            repoSource, filePaths.size(), allDetails.size(), duration);
        
        return CompletableFuture.completedFuture(allDetails);
    }
    
    /**
     * 获取单个文件的详细提交历史 - 保持向后兼容
     */
    private List<FileCommitDetail> getFileCommitDetails(String filePath, String repoSource, 
                                                     RepoConfig repoConfig, 
                                                     Instant timeFrom, 
                                                     Instant timeTo) {
        List<FileCommitDetail> details = new ArrayList<>();
        
        if (repoConfig == null) {
            log.debug("Repository config is null for repo source: {}, skipping file: {}", repoSource, filePath);
            return details;
        }
        
        try {
            // 调用GitCommitHistoryService获取提交历史
            FileCommitHistoryDTO commitHistory = gitCommitHistoryService.getFileCommitHistory(
                    filePath, 
                    "Oracle".equals(repoSource) ? repoConfig : null, 
                    "Gauss".equals(repoSource) ? repoConfig : null);
            
            // 根据repoSource获取对应的提交列表
            List<GitCommitInfoDTO> commits = "Oracle".equals(repoSource) 
                    ? commitHistory.getOracleCommits() 
                    : commitHistory.getGaussCommits();
            
            // 转换为FileCommitDetail并按时间范围过滤
            for (GitCommitInfoDTO commit : commits) {
                // 检查是否在时间范围内
                boolean inTimeRange = true;
                if (timeFrom != null && commit.getCommitTime().isBefore(timeFrom)) {
                    inTimeRange = false;
                }
                if (timeTo != null && commit.getCommitTime().isAfter(timeTo)) {
                    inTimeRange = false;
                }
                
                // 提取提交类型
                String commitType = extractCommitType(commit.getMessage());
                
                FileCommitDetail detail = FileCommitDetail.builder()
                        .filePath(filePath)
                        .repoSource(repoSource)
                        .commitHash(commit.getCommitHash())
                        .authorName(commit.getAuthorName())
                        .authorEmail(commit.getAuthorEmail())
                        .commitTime(commit.getCommitTime())
                        .commitMessage(commit.getMessage())
                        .commitType(commitType)
                        .changeType("MODIFY") // 默认为修改，可根据实际情况调整
                        .linesAdded(commit.getStats() != null ? commit.getStats().getAdded() : 0)
                        .linesRemoved(commit.getStats() != null ? commit.getStats().getRemoved() : 0)
                        .inTimeRange(inTimeRange)
                        .build();
                
                details.add(detail);
            }
            
        } catch (Exception e) {
            log.warn("获取文件提交历史失败: {}, 文件: {}, 仓库: {}", e.getMessage(), filePath, repoSource);
        }
        
        return details;
    }
    
    /**
     * 从提交消息中提取提交类型
     */
    private String extractCommitType(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "other";
        }
        
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.startsWith("feat") || lowerMessage.startsWith("feature")) {
            return "feature";
        } else if (lowerMessage.startsWith("fix") || lowerMessage.startsWith("bugfix")) {
            return "bugfix";
        } else if (lowerMessage.startsWith("refactor") || lowerMessage.startsWith("refactoring")) {
            return "refactor";
        } else if (lowerMessage.startsWith("hotfix")) {
            return "hotfix";
        } else if (lowerMessage.startsWith("test") || lowerMessage.startsWith("tests")) {
            return "test";
        } else if (lowerMessage.startsWith("doc") || lowerMessage.startsWith("docs")) {
            return "docs";
        } else if (lowerMessage.startsWith("style") || lowerMessage.startsWith("format")) {
            return "style";
        } else if (lowerMessage.startsWith("chore") || lowerMessage.startsWith("build")) {
            return "chore";
        } else {
            return "other";
        }
    }

    private String resolveDisplayName(MultiRepoExportRequest.RepoSelection selection, ScanReport report) {
        if (selection != null && StringUtils.hasText(selection.getAlias())) {
            return selection.getAlias();
        }
        if (StringUtils.hasText(report.getPresetName())) {
            return report.getPresetName();
        }
        String repoPath = report.getOracleSummary() != null && report.getOracleSummary().getRepoPath() != null
                ? report.getOracleSummary().getRepoPath()
                : null;
        if (StringUtils.hasText(repoPath)) {
            return repoPath;
        }
        return "task-" + report.getTaskId().toLowerCase(Locale.ROOT);
    }
}
