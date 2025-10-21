package com.example.codecompare.rebuild.api.controller;

import com.example.codecompare.rebuild.api.dto.MigrationOverviewView;
import com.example.codecompare.rebuild.api.mapper.MigrationViewMapper;
import com.example.codecompare.rebuild.api.response.ApiResponse;
import com.example.codecompare.rebuild.api.response.ApiResponseFactory;
import com.example.codecompare.rebuild.core.config.ConfigurationReloadReport;
import com.example.codecompare.rebuild.core.config.ConfigurationRefreshCoordinator;
import com.example.codecompare.rebuild.scanning.FileScanService;
import com.example.codecompare.rebuild.scanning.ScanProperties;
import com.example.codecompare.rebuild.scanning.ScanSummary;
import com.example.codecompare.rebuild.stats.DashboardSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoints that expose project level overview data.
 */
@RestController
@RequestMapping("/api/v1/migration")
public class MigrationDashboardController {

    private static final Logger log = LoggerFactory.getLogger(MigrationDashboardController.class);

    private final DashboardSummaryService dashboardSummaryService;
    private final MigrationViewMapper viewMapper;
    private final FileScanService fileScanService;
    private final ConfigurationRefreshCoordinator configurationRefreshCoordinator;
    private final ScanProperties scanProperties;

    public MigrationDashboardController(DashboardSummaryService dashboardSummaryService,
                                        MigrationViewMapper viewMapper,
                                        FileScanService fileScanService,
                                        ConfigurationRefreshCoordinator configurationRefreshCoordinator,
                                        ScanProperties scanProperties) {
        this.dashboardSummaryService = dashboardSummaryService;
        this.viewMapper = viewMapper;
        this.fileScanService = fileScanService;
        this.configurationRefreshCoordinator = configurationRefreshCoordinator;
        this.scanProperties = scanProperties;
    }

    @GetMapping("/overview")
    public ApiResponse<MigrationOverviewView> loadOverview(
            @RequestParam(value = "projectKey", required = false) String projectKey,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        ConfigurationReloadReport configReport = configurationRefreshCoordinator.reloadAll();
        String effectiveProject = StringUtils.hasText(projectKey) ? projectKey : null;
        if (refresh) {
            boolean gitEnabled = scanProperties != null
                    && "git".equalsIgnoreCase(StringUtils.trimWhitespace(scanProperties.getDiffEngine()));
            ScanSummary summary;
            String message;
            if (gitEnabled) {
                log.info("Received dashboard refresh request. Executing Git incremental scan, project={}", effectiveProject);
                summary = fileScanService.scanIncremental(effectiveProject);
                if (summary == null) {
                    log.warn("Git incremental scan produced no summary, falling back to full rescan. project={}", effectiveProject);
                    summary = fileScanService.reload(effectiveProject);
                    message = "Git 增量扫描失败，已回退到全量重新扫描";
                } else {
                    message = "Git 增量重新扫描完成";
                }
            } else {
                log.info("Received dashboard refresh request. Executing full rescan, project={}", effectiveProject);
                summary = fileScanService.reload(effectiveProject);
                message = "全量重新扫描完成";
            }
            String followUpProject = summary == null
                    ? effectiveProject
                    : (StringUtils.hasText(effectiveProject) ? effectiveProject : summary.getProjectCode());
            MigrationOverviewView refreshed = viewMapper.toOverviewView(
                    dashboardSummaryService.overview(followUpProject),
                    configReport);
            return ApiResponseFactory.ok(message, refreshed);
        }

        MigrationOverviewView view = viewMapper.toOverviewView(
                dashboardSummaryService.overview(effectiveProject),
                configReport);
        if (needsAutoRefresh(view)) {
            log.info("Dashboard overview is empty, prompting client to trigger a refresh. project={}", effectiveProject);
            return ApiResponseFactory.ok("当前没有可用数据，请执行重新加载以生成最新内容", view);
        }
        return ApiResponseFactory.ok(view);
    }

    private boolean needsAutoRefresh(MigrationOverviewView view) {
        if (view == null) {
            return true;
        }
        boolean emptyMetrics = view.getTotalBlocks() == 0 && view.getTotalLines() == 0;
        boolean missingCategories = view.getCodeCategoryStats() == null || view.getCodeCategoryStats().isEmpty();
        boolean missingTimeline = view.getLastSyncedAt() == null;
        return emptyMetrics && missingCategories && missingTimeline;
    }
}
