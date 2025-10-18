package com.example.codecompare.rebuild.api.controller;

import com.example.codecompare.rebuild.api.dto.MigrationOverviewView;
import com.example.codecompare.rebuild.api.mapper.MigrationViewMapper;
import com.example.codecompare.rebuild.api.response.ApiResponse;
import com.example.codecompare.rebuild.api.response.ApiResponseFactory;
import com.example.codecompare.rebuild.core.config.ConfigurationReloadReport;
import com.example.codecompare.rebuild.core.config.ConfigurationRefreshCoordinator;
import com.example.codecompare.rebuild.scanning.FileScanService;
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

    public MigrationDashboardController(DashboardSummaryService dashboardSummaryService,
                                        MigrationViewMapper viewMapper,
                                        FileScanService fileScanService,
                                        ConfigurationRefreshCoordinator configurationRefreshCoordinator) {
        this.dashboardSummaryService = dashboardSummaryService;
        this.viewMapper = viewMapper;
        this.fileScanService = fileScanService;
        this.configurationRefreshCoordinator = configurationRefreshCoordinator;
    }

    @GetMapping("/overview")
    public ApiResponse<MigrationOverviewView> loadOverview(
            @RequestParam(value = "projectKey", required = false) String projectKey,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        ConfigurationReloadReport configReport = configurationRefreshCoordinator.reloadAll();
        String effectiveProject = StringUtils.hasText(projectKey) ? projectKey : null;
        if (refresh) {
            log.info("收到重新加载请求，先清理后扫描，projectKey={}", effectiveProject);
            ScanSummary summary = fileScanService.reload(effectiveProject);
            String followUpProject = StringUtils.hasText(effectiveProject) ? effectiveProject : summary.getProjectCode();
            MigrationOverviewView view = viewMapper.toOverviewView(
                    dashboardSummaryService.overview(followUpProject),
                    configReport);
            return ApiResponseFactory.ok("重新加载成功", view);
        }
        MigrationOverviewView view = viewMapper.toOverviewView(
                dashboardSummaryService.overview(effectiveProject),
                configReport);
        if (needsAutoRefresh(view)) {
            log.info("仪表盘数据为空，请执行重新加载以生成最新内容，projectKey={}", effectiveProject);
            return ApiResponseFactory.ok("暂无数据，请执行重新加载以生成最新内容", view);
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
