package com.example.migratediff.application.scan;

import com.example.migratediff.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 负责将多个任务的扫描结果聚合为导出视图。
 */
@Service
@Slf4j
public class MultiRepoExportService {

    private final ScanResultStore scanResultStore;
    private final DiffMatrixAssembler diffMatrixAssembler;
    private final DiffMatrixFilter diffMatrixFilter;

    public MultiRepoExportService(ScanResultStore scanResultStore,
                                  DiffMatrixAssembler diffMatrixAssembler,
                                  DiffMatrixFilter diffMatrixFilter) {
        this.scanResultStore = scanResultStore;
        this.diffMatrixAssembler = diffMatrixAssembler;
        this.diffMatrixFilter = diffMatrixFilter;
    }

    public MultiRepoExportResult export(MultiRepoExportRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getRepos())) {
            throw new IllegalArgumentException("缺少有效的仓库列表");
        }
        DiffMatrixFilterCriteria criteria = buildCriteria(request);
        List<MultiRepoExportResult.RepoReport> repoReports = new ArrayList<>();
        for (MultiRepoExportRequest.RepoSelection selection : request.getRepos()) {
            ScanReport report = resolveReport(selection);
            List<DiffMatrixRow> rows = diffMatrixFilter.filter(
                    diffMatrixAssembler.assemble(report),
                    criteria);
            MultiRepoExportResult.RepoStats stats = buildStats(report, rows);
            repoReports.add(MultiRepoExportResult.RepoReport.builder()
                    .displayName(resolveDisplayName(selection, report))
                    .scanReport(report)
                    .rows(rows)
                    .stats(stats)
                    .build());
        }
        return MultiRepoExportResult.builder()
                .generatedAt(Instant.now())
                .filterCriteria(criteria)
                .repoReports(repoReports)
                .build();
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
