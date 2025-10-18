package com.example.codecompare.rebuild.stats;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.scanning.ScanSummary;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;

/**
 * 浠〃鐩樻瑕佹湇鍔★紝鑱氬悎鎵弿涓庡樊寮傛寚鏍囥€?
 */
@Service
public class DashboardSummaryService {

    private final MetricsAggregator metricsAggregator;
    private final StatsViewMapper statsViewMapper;
    private final ProjectRootRegistry projectRootRegistry;

    public DashboardSummaryService(MetricsAggregator metricsAggregator,
                                   StatsViewMapper statsViewMapper,
                                   ProjectRootRegistry projectRootRegistry) {
        this.metricsAggregator = metricsAggregator;
        this.statsViewMapper = statsViewMapper;
        this.projectRootRegistry = projectRootRegistry;
    }

    public DashboardOverviewDTO overview(String projectCode) {
        String code = StringUtils.hasText(projectCode) ? projectCode : resolveDefaultProjectCode();
        ScanSummary summary = metricsAggregator.latestSummary(code);
        MetricsAggregator.CategoryMetrics metrics = metricsAggregator.categoryMetrics(code);
        return statsViewMapper.toDashboardOverview(code, summary, metrics);
    }

    private String resolveDefaultProjectCode() {
        List<Path> roots = projectRootRegistry.getRoots();
        if (!CollectionUtils.isEmpty(roots)) {
            Path first = roots.get(0);
            if (first != null && first.getFileName() != null) {
                return first.getFileName().toString();
            }
        }
        return "default-project";
    }
}
