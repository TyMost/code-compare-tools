package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.FileCommitHistoryDTO;
import com.example.migratediff.api.dto.GitCommitInfoDTO;
import com.example.migratediff.application.commit.GitCommitHistoryService;
import com.example.migratediff.config.MigrationDiffProperties;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoType;
import com.example.migratediff.shared.exception.NotFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
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
        
        // 第一步：解析所有仓库报告，构建基础数据结构
        List<ResolvedRepoData> resolvedRepoData = new ArrayList<>();
        for (MultiRepoExportRequest.RepoSelection selection : request.getRepos()) {
            ScanReport report = resolveReport(selection);
            List<DiffMatrixRow> rows = diffMatrixFilter.filter(
                    diffMatrixAssembler.assemble(report),
                    criteria);
            
            MultiRepoExportResult.RepoStats stats = buildStats(report, rows);
            
            ResolvedRepoData repoData = ResolvedRepoData.builder()
                    .selection(selection)
                    .report(report)
                    .rows(rows)
                    .stats(stats)
                    .oracleRepo(extractRepoConfig(report, true))
                    .gaussRepo(extractRepoConfig(report, false))
                    .timeFrom(extractTimeFromByPreset(report.getPresetName()))
                    .timeTo(extractTimeToByPreset(report.getPresetName()))
                    .build();
            
            resolvedRepoData.add(repoData);
        }
        
        // 第二步：批量获取所有仓库的提交信息（如果需要）
        Map<String, Map<String, List<GitCommitInfoDTO>>> allBatchCommits = new HashMap<>();
        if (criteria != null && criteria.isIncludeCommitInfo()) {
            allBatchCommits = batchGetAllRepoCommits(resolvedRepoData);
        }
        
        // 第三步：使用批量数据丰富仓库报告和统计信息
        for (ResolvedRepoData repoData : resolvedRepoData) {
            // 使用批量获取的数据丰富行信息
            List<DiffMatrixRow> enrichedRows = enrichRowsWithBatchData(
                repoData.getRows(), repoData, allBatchCommits);
            
            // 使用批量获取的数据聚合提交者统计
            List<CommitAuthorStats> authorStats = aggregateAuthorStatsWithBatchData(
                enrichedRows, repoData, allBatchCommits);
            allAuthorStats.addAll(authorStats);
            
            repoReports.add(MultiRepoExportResult.RepoReport.builder()
                    .displayName(resolveDisplayName(repoData.getSelection(), repoData.getReport()))
                    .scanReport(repoData.getReport())
                    .rows(enrichedRows)
                    .stats(repoData.getStats())
                    .authorStats(authorStats)
                    .build());
        }
        
        // 第四步：生成所有文件的详细提交历史（如果需要）
        List<FileCommitDetail> allFileCommitDetails = new ArrayList<>();
        if (criteria != null && criteria.isIncludeCommitInfo()) {
            allFileCommitDetails = buildAllFileCommitDetails(resolvedRepoData, allBatchCommits);
        }
        
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
     * 聚合文件详细提交历史 - 使用批量获取优化，支持开关控制
     */
    private List<FileCommitDetail> aggregateFileCommitDetails(List<MultiRepoExportResult.RepoReport> repoReports, DiffMatrixFilterCriteria criteria) {
        // 检查是否需要包含提交信息
        if (criteria != null && !criteria.isIncludeCommitInfo()) {
            log.info("跳过文件提交历史聚合，根据用户设置");
            return new ArrayList<>();
        }
        
        List<FileCommitDetail> allDetails = new ArrayList<>();
        
        log.info("开始批量聚合文件提交历史，仓库数量: {}, 使用批量优化", repoReports.size());
        
        // 使用并行处理，但每个仓库只创建一个批量任务
        List<CompletableFuture<List<FileCommitDetail>>> futures = new ArrayList<>();
        
        for (MultiRepoExportResult.RepoReport repo : repoReports) {
            ScanReport report = repo.getScanReport();
            RepoConfig oracleRepo = extractRepoConfig(report, true);
            RepoConfig gaussRepo = extractRepoConfig(report, false);
            
            // 获取该仓库的时间范围
            Instant timeFrom = extractTimeFromByPreset(report.getPresetName());
            Instant timeTo = extractTimeToByPreset(report.getPresetName());
            
            // 获取需要处理的文件路径列表
            Set<String> allFilePaths = repo.getRows().stream()
                .map(DiffMatrixRow::getFilePath)
                .collect(Collectors.toSet());
            
            log.debug("批量处理仓库报告: 预设={}, 文件数量: {}", 
                report.getPresetName(), allFilePaths.size());
            
            // 为Oracle仓库创建批量获取任务
            if (oracleRepo != null && !allFilePaths.isEmpty()) {
                CompletableFuture<List<FileCommitDetail>> oracleFuture = 
                    getBatchFileCommitDetailsOptimizedAsync(allFilePaths, "Oracle", oracleRepo, timeFrom, timeTo);
                futures.add(oracleFuture);
            }
            
            // 为Gauss仓库创建批量获取任务
            if (gaussRepo != null && !allFilePaths.isEmpty()) {
                CompletableFuture<List<FileCommitDetail>> gaussFuture = 
                    getBatchFileCommitDetailsOptimizedAsync(allFilePaths, "Gauss", gaussRepo, timeFrom, timeTo);
                futures.add(gaussFuture);
            }
        }
        
        // 等待所有批量任务完成
        for (CompletableFuture<List<FileCommitDetail>> future : futures) {
            try {
                List<FileCommitDetail> details = future.get();
                allDetails.addAll(details);
                log.debug("批量任务完成，获取到 {} 个提交", details.size());
            } catch (Exception e) {
                log.error("批量获取提交历史失败", e);
            }
        }
        
        // 按时间倒序排序
        List<FileCommitDetail> sortedDetails = allDetails.stream()
                .sorted((a, b) -> b.getCommitTime().compareTo(a.getCommitTime()))
                .collect(Collectors.toList());
        
        log.info("文件提交历史批量聚合完成: 总计 {} 个提交，使用了 {} 个批量任务", 
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
     * 优化的异步批量获取文件提交历史 - 使用仓库级别批量获取
     */
    @Async("commitInfoExecutor")
    public CompletableFuture<List<FileCommitDetail>> getBatchFileCommitDetailsOptimizedAsync(
            Set<String> filePaths, String repoSource, 
            RepoConfig repoConfig, 
            Instant timeFrom, 
            Instant timeTo) {
        
        List<FileCommitDetail> allDetails = new ArrayList<>();
        
        if (repoConfig == null || filePaths.isEmpty()) {
            log.debug("Repository config is null or file list is empty for repo source: {}", repoSource);
            return CompletableFuture.completedFuture(allDetails);
        }
        
        long startTime = System.currentTimeMillis();
        log.info("开始批量获取{}仓库的提交历史: {} 个文件，时间范围: {} 至 {}", 
            repoSource, filePaths.size(), timeFrom, timeTo);
        
        try {
            // 使用仓库级别批量获取方法
            RepoType repoType = "Oracle".equals(repoSource) ? RepoType.ORACLE : RepoType.GAUSS;
            Map<String, List<GitCommitInfoDTO>> allRepoCommits = gitCommitHistoryService.getRepoAllCommits(
                repoConfig, repoType, timeFrom, timeTo);
            
            int processedFiles = 0;
            int totalCommits = 0;
            
            // 在内存中按文件筛选和转换
            for (String filePath : filePaths) {
                List<GitCommitInfoDTO> fileCommits = allRepoCommits.get(filePath);
                if (fileCommits != null && !fileCommits.isEmpty()) {
                    // 转换为FileCommitDetail
                    for (GitCommitInfoDTO commit : fileCommits) {
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
                                .changeType("MODIFY")
                                .linesAdded(commit.getStats() != null ? commit.getStats().getAdded() : 0)
                                .linesRemoved(commit.getStats() != null ? commit.getStats().getRemoved() : 0)
                                .inTimeRange(true) // 在 getRepoAllCommits 中已经过滤了时间范围
                                .build();
                        
                        allDetails.add(detail);
                        totalCommits++;
                    }
                    processedFiles++;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("{}仓库批量获取完成: 处理 {} 个文件, {} 个提交, 耗时: {}ms", 
                repoSource, processedFiles, totalCommits, duration);
            
        } catch (Exception e) {
            log.error("批量获取{}仓库提交历史失败: {}", repoSource, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(allDetails);
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

    /**
     * 批量获取所有仓库的提交信息
     */
    private Map<String, Map<String, List<GitCommitInfoDTO>>> batchGetAllRepoCommits(List<ResolvedRepoData> resolvedRepoData) {
        Map<String, Map<String, List<GitCommitInfoDTO>>> allBatchCommits = new HashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        log.info("开始批量获取所有仓库的提交信息，仓库数量: {}", resolvedRepoData.size());
        long startTime = System.currentTimeMillis();
        
        for (ResolvedRepoData repoData : resolvedRepoData) {
            String repoKey = repoData.getReport().getTaskId();
            
            // 并行获取Oracle仓库提交
            if (repoData.getOracleRepo() != null) {
                CompletableFuture<Void> oracleFuture = CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, List<GitCommitInfoDTO>> oracleCommits = gitCommitHistoryService.getRepoAllCommits(
                            repoData.getOracleRepo(), RepoType.ORACLE, repoData.getTimeFrom(), repoData.getTimeTo());
                        synchronized (allBatchCommits) {
                            allBatchCommits.put(repoKey + "_oracle", oracleCommits);
                        }
                        log.debug("Oracle仓库批量获取完成: {}, 预设: {}", repoKey, repoData.getReport().getPresetName());
                    } catch (Exception e) {
                        log.error("Oracle仓库批量获取失败: {}", repoKey, e);
                    }
                });
                futures.add(oracleFuture);
            }
            
            // 并行获取Gauss仓库提交
            if (repoData.getGaussRepo() != null) {
                CompletableFuture<Void> gaussFuture = CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, List<GitCommitInfoDTO>> gaussCommits = gitCommitHistoryService.getRepoAllCommits(
                            repoData.getGaussRepo(), RepoType.GAUSS, repoData.getTimeFrom(), repoData.getTimeTo());
                        synchronized (allBatchCommits) {
                            allBatchCommits.put(repoKey + "_gauss", gaussCommits);
                        }
                        log.debug("Gauss仓库批量获取完成: {}, 预设: {}", repoKey, repoData.getReport().getPresetName());
                    } catch (Exception e) {
                        log.error("Gauss仓库批量获取失败: {}", repoKey, e);
                    }
                });
                futures.add(gaussFuture);
            }
        }
        
        // 等待所有批量获取完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("所有仓库批量获取完成: {} 个仓库, 耗时: {}ms", resolvedRepoData.size(), duration);
        
        return allBatchCommits;
    }

    /**
     * 使用批量数据丰富行信息
     */
    private List<DiffMatrixRow> enrichRowsWithBatchData(List<DiffMatrixRow> rows, ResolvedRepoData repoData, 
                                                       Map<String, Map<String, List<GitCommitInfoDTO>>> allBatchCommits) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        
        String repoKey = repoData.getReport().getTaskId();
        Map<String, List<GitCommitInfoDTO>> oracleCommits = allBatchCommits.get(repoKey + "_oracle");
        Map<String, List<GitCommitInfoDTO>> gaussCommits = allBatchCommits.get(repoKey + "_gauss");
        
        return rows.stream().map(row -> {
            String filePath = row.getFilePath();
            
            // 丰富Oracle信息
            if (oracleCommits != null) {
                List<GitCommitInfoDTO> fileOracleCommits = oracleCommits.get(filePath);
                if (fileOracleCommits != null && !fileOracleCommits.isEmpty()) {
                    GitCommitInfoDTO lastCommit = fileOracleCommits.get(0);
                    row.setLastOracleAuthor(lastCommit.getAuthorName());
                    row.setLastOracleCommitTime(lastCommit.getCommitTime());
                    row.setLastOracleCommitMessage(truncateMessage(lastCommit.getMessage()));
                    row.setLastOracleCommitHash(lastCommit.getShortHash());
                    row.setOracleCommitCount(fileOracleCommits.size());
                    row.setHasOracleCommits(true);
                } else {
                    row.setHasOracleCommits(false);
                    row.setOracleCommitCount(0);
                }
            }
            
            // 丰富Gauss信息
            if (gaussCommits != null) {
                List<GitCommitInfoDTO> fileGaussCommits = gaussCommits.get(filePath);
                if (fileGaussCommits != null && !fileGaussCommits.isEmpty()) {
                    GitCommitInfoDTO lastCommit = fileGaussCommits.get(0);
                    row.setLastGaussAuthor(lastCommit.getAuthorName());
                    row.setLastGaussCommitTime(lastCommit.getCommitTime());
                    row.setLastGaussCommitMessage(truncateMessage(lastCommit.getMessage()));
                    row.setLastGaussCommitHash(lastCommit.getShortHash());
                    row.setGaussCommitCount(fileGaussCommits.size());
                    row.setHasGaussCommits(true);
                } else {
                    row.setHasGaussCommits(false);
                    row.setGaussCommitCount(0);
                }
            }
            
            return row;
        }).collect(Collectors.toList());
    }

    /**
     * 使用批量数据聚合提交者统计
     */
    private List<CommitAuthorStats> aggregateAuthorStatsWithBatchData(List<DiffMatrixRow> rows, ResolvedRepoData repoData,
                                                                      Map<String, Map<String, List<GitCommitInfoDTO>>> allBatchCommits) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        
        String repoKey = repoData.getReport().getTaskId();
        String repoName = repoData.getReport().getPresetName() != null ? repoData.getReport().getPresetName() : repoData.getReport().getTaskId();
        
        Map<String, List<GitCommitInfoDTO>> oracleCommits = allBatchCommits.get(repoKey + "_oracle");
        Map<String, List<GitCommitInfoDTO>> gaussCommits = allBatchCommits.get(repoKey + "_gauss");
        
        return commitInfoAggregatorService.aggregateAuthorStatsWithBatchData(
            rows, oracleCommits, gaussCommits, repoName);
    }

    /**
     * 构建所有文件的详细提交历史
     */
    private List<FileCommitDetail> buildAllFileCommitDetails(List<ResolvedRepoData> resolvedRepoData,
                                                            Map<String, Map<String, List<GitCommitInfoDTO>>> allBatchCommits) {
        List<FileCommitDetail> allDetails = new ArrayList<>();
        
        for (ResolvedRepoData repoData : resolvedRepoData) {
            String repoKey = repoData.getReport().getTaskId();
            Map<String, List<GitCommitInfoDTO>> oracleCommits = allBatchCommits.get(repoKey + "_oracle");
            Map<String, List<GitCommitInfoDTO>> gaussCommits = allBatchCommits.get(repoKey + "_gauss");
            
            // 转换Oracle提交详情
            if (oracleCommits != null) {
                for (Map.Entry<String, List<GitCommitInfoDTO>> entry : oracleCommits.entrySet()) {
                    String filePath = entry.getKey();
                    for (GitCommitInfoDTO commit : entry.getValue()) {
                        FileCommitDetail detail = FileCommitDetail.builder()
                                .filePath(filePath)
                                .repoSource("Oracle")
                                .commitHash(commit.getCommitHash())
                                .authorName(commit.getAuthorName())
                                .authorEmail(commit.getAuthorEmail())
                                .commitTime(commit.getCommitTime())
                                .commitMessage(commit.getMessage())
                                .commitType(extractCommitType(commit.getMessage()))
                                .changeType("MODIFY")
                                .linesAdded(commit.getStats() != null ? commit.getStats().getAdded() : 0)
                                .linesRemoved(commit.getStats() != null ? commit.getStats().getRemoved() : 0)
                                .inTimeRange(true)
                                .build();
                        allDetails.add(detail);
                    }
                }
            }
            
            // 转换Gauss提交详情
            if (gaussCommits != null) {
                for (Map.Entry<String, List<GitCommitInfoDTO>> entry : gaussCommits.entrySet()) {
                    String filePath = entry.getKey();
                    for (GitCommitInfoDTO commit : entry.getValue()) {
                        FileCommitDetail detail = FileCommitDetail.builder()
                                .filePath(filePath)
                                .repoSource("Gauss")
                                .commitHash(commit.getCommitHash())
                                .authorName(commit.getAuthorName())
                                .authorEmail(commit.getAuthorEmail())
                                .commitTime(commit.getCommitTime())
                                .commitMessage(commit.getMessage())
                                .commitType(extractCommitType(commit.getMessage()))
                                .changeType("MODIFY")
                                .linesAdded(commit.getStats() != null ? commit.getStats().getAdded() : 0)
                                .linesRemoved(commit.getStats() != null ? commit.getStats().getRemoved() : 0)
                                .inTimeRange(true)
                                .build();
                        allDetails.add(detail);
                    }
                }
            }
        }
        
        // 按时间倒序排序
        return allDetails.stream()
                .sorted((a, b) -> b.getCommitTime().compareTo(a.getCommitTime()))
                .collect(Collectors.toList());
    }

    /**
     * 截断提交消息
     */
    private String truncateMessage(String message) {
        if (message == null) return "";
        String trimmed = message.trim();
        if (trimmed.length() <= 50) return trimmed;
        return trimmed.substring(0, 47) + "...";
    }

    /**
     * 解析后的仓库数据容器
     */
    @Data
    @Builder
    private static class ResolvedRepoData {
        private MultiRepoExportRequest.RepoSelection selection;
        private ScanReport report;
        private List<DiffMatrixRow> rows;
        private MultiRepoExportResult.RepoStats stats;
        private RepoConfig oracleRepo;
        private RepoConfig gaussRepo;
        private Instant timeFrom;
        private Instant timeTo;
    }
}
