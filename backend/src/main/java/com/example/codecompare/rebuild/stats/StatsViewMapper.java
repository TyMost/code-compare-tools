package com.example.codecompare.rebuild.stats;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootType;
import com.example.codecompare.rebuild.stats.MetricsAggregator.BlockItem;
import com.example.codecompare.rebuild.stats.MetricsAggregator.CategoryCount;
import com.example.codecompare.rebuild.stats.MetricsAggregator.CategoryMetrics;
import com.example.codecompare.rebuild.stats.MetricsAggregator.CodeBlockPage;
import com.example.codecompare.rebuild.scanning.ScanSummary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 统计模块视图对象映射器，负责将领域模型转换为前端所需 DTO。
 */
@Component
public class StatsViewMapper {

    private static final String[] PALETTE = new String[]{
            "#2563eb", "#16a34a", "#f97316", "#7c3aed", "#0ea5e9",
            "#facc15", "#14b8a6", "#f43f5e", "#8b5cf6", "#22d3ee"
    };

    private final CategoryLabelResolver categoryLabelResolver;
    private final ProjectRootRegistry projectRootRegistry;

    public StatsViewMapper(CategoryLabelResolver categoryLabelResolver,
                           ProjectRootRegistry projectRootRegistry) {
        this.categoryLabelResolver = categoryLabelResolver;
        this.projectRootRegistry = projectRootRegistry;
    }

    public DashboardOverviewDTO toDashboardOverview(String projectCode,
                                                    ScanSummary summary,
                                                    CategoryMetrics metrics) {
        List<String> roots = summary == null ? Collections.emptyList() : summary.getScannedRoots();
        String oldPath = resolvePath(roots, 0);
        String newPath = resolvePath(roots, 1);

        PathPair pair = resolveFallbackPaths(projectCode, oldPath, newPath);
        oldPath = pair.oldPath;
        newPath = pair.newPath;

        if (!StringUtils.hasText(oldPath)) {
            oldPath = projectCode;
        }
        if (!StringUtils.hasText(newPath)) {
            newPath = oldPath;
        }
        DashboardOverviewDTO.Builder builder = DashboardOverviewDTO.builder()
                .projectCode(projectCode)
                .oldProjectPath(oldPath)
                .newProjectPath(newPath)
                .lastSyncedAt(summary == null ? null : summary.getCompletedAt())
                .diffEngine(summary == null ? "default" : summary.getDiffEngine())
                .gitSourceChangedLines(metrics == null ? 0L : metrics.getGitSourceChangedLines())
                .gitTargetChangedLines(metrics == null ? 0L : metrics.getGitTargetChangedLines())
                .totalLines(metrics == null ? 0 : metrics.getTotalLines())
                .totalBlocks(metrics == null ? 0 : metrics.getTotalBlocks());
        if (metrics != null) {
            builder.categoryStats(toCategoryStats(metrics));
        }
        return builder.build();
    }

    public CodeBlockPageDTO toCodeBlockPage(CodeBlockPage page) {
        List<CodeBlockItemDTO> items = new ArrayList<>();
        for (BlockItem item : page.getItems()) {
            String statusColor = item.getStatusColor();
            if (!StringUtils.hasText(statusColor)) {
                statusColor = categoryLabelResolver.resolveColor(item.getStatus());
            }
            if (!StringUtils.hasText(statusColor)) {
                statusColor = "#9ca3af";
            }
            items.add(new CodeBlockItemDTO(
                    item.getId(),
                    item.getFilePath(),
                    item.getStartLine(),
                    item.getEndLine(),
                    item.getCodeSnippet(),
                    toCategoryFilters(item.getCategories()),
                    item.getStatus(),
                    item.getStatusLabel(),
                    statusColor
            ));
        }
        return new CodeBlockPageDTO(items, page.getPage(), page.getSize(), page.getTotal(), page.getTotalPages());
    }

    public List<CategoryFilterDTO> toCategoryFilters(MetricsAggregator.CodeBlockPage page) {
        List<CategoryFilterDTO> filters = new ArrayList<>();
        int index = 0;
        for (String key : page.getCategoryKeys()) {
            filters.add(new CategoryFilterDTO(
                    key,
                    categoryLabelResolver.resolve(key),
                    resolveColor(key, index++)));
        }
        return filters;
    }

    private List<CategoryFilterDTO> toCategoryFilters(List<String> categories) {
        if (CollectionUtils.isEmpty(categories)) {
            return Collections.emptyList();
        }
        List<CategoryFilterDTO> result = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            String label = categories.get(i);
            result.add(new CategoryFilterDTO(
                    label,
                    categoryLabelResolver.resolve(label),
                    resolveColor(label, i)));
        }
        return result;
    }

    private List<CategoryStatDTO> toCategoryStats(CategoryMetrics metrics) {
        List<CategoryStatDTO> result = new ArrayList<>();
        List<CategoryCount> categories = metrics.getCategories();
        long totalLines = metrics.getTotalLines() == 0
                ? categories.stream().mapToLong(CategoryCount::getLineCount).sum()
                : metrics.getTotalLines();
        if (totalLines == 0) {
            totalLines = 1;
        }
        for (int i = 0; i < categories.size(); i++) {
            CategoryCount count = categories.get(i);
            double ratio = Math.max(0d, Math.min(1d, (double) count.getLineCount() / totalLines));
            result.add(new CategoryStatDTO(
                    count.getKey(),
                    categoryLabelResolver.resolve(count.getKey()),
                    count.getLineCount(),
                    ratio,
                    resolveColor(count.getKey(), i)
            ));
        }
        return result;
    }

    private PathPair resolveFallbackPaths(String projectCode, String currentOld, String currentNew) {
        String resolvedOld = currentOld;
        String resolvedNew = currentNew;

        Optional<ProjectRootDescriptor> descriptorOptional = projectRootRegistry.findByCode(projectCode);
        List<ProjectRootDescriptor> sources = projectRootRegistry.getSources();
        List<ProjectRootDescriptor> targets = projectRootRegistry.getTargets();

        if (descriptorOptional.isPresent()) {
            ProjectRootDescriptor descriptor = descriptorOptional.get();
            if (descriptor.getType() == ProjectRootType.SOURCE) {
                String descriptorPath = pathToString(descriptor.getPath());
                if (!StringUtils.hasText(resolvedOld)) {
                    resolvedOld = descriptorPath;
                }
                if (!StringUtils.hasText(resolvedNew) || samePath(resolvedOld, resolvedNew)) {
                    ProjectRootDescriptor targetDescriptor = resolveByIndex(targets, sources.indexOf(descriptor));
                    resolvedNew = pathToString(descriptorPath(targetDescriptor));
                }
            } else if (descriptor.getType() == ProjectRootType.TARGET) {
                String descriptorPath = pathToString(descriptor.getPath());
                if (!StringUtils.hasText(resolvedNew)) {
                    resolvedNew = descriptorPath;
                }
                if (!StringUtils.hasText(resolvedOld) || samePath(resolvedOld, resolvedNew)) {
                    ProjectRootDescriptor sourceDescriptor = resolveByIndex(sources, targets.indexOf(descriptor));
                    resolvedOld = pathToString(descriptorPath(sourceDescriptor));
                }
            }
        }

        if (!StringUtils.hasText(resolvedOld)) {
            resolvedOld = pathToString(descriptorPath(resolveByIndex(sources, 0)));
        }
        if (!StringUtils.hasText(resolvedNew) || samePath(resolvedOld, resolvedNew)) {
            String fallbackTarget = pathToString(descriptorPath(resolveByIndex(targets, 0)));
            if (StringUtils.hasText(fallbackTarget) && !samePath(resolvedOld, fallbackTarget)) {
                resolvedNew = fallbackTarget;
            }
        }

        return new PathPair(resolvedOld, resolvedNew);
    }

    private ProjectRootDescriptor resolveByIndex(List<ProjectRootDescriptor> descriptors, int index) {
        if (index < 0 || CollectionUtils.isEmpty(descriptors) || index >= descriptors.size()) {
            return CollectionUtils.isEmpty(descriptors) ? null : descriptors.get(0);
        }
        return descriptors.get(index);
    }

    private boolean samePath(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return normalizePath(left).equals(normalizePath(right));
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
    }

    private java.nio.file.Path descriptorPath(ProjectRootDescriptor descriptor) {
        return descriptor == null ? null : descriptor.getPath();
    }

    private String pathToString(java.nio.file.Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }

    private String resolvePath(List<String> roots, int index) {
        if (CollectionUtils.isEmpty(roots) || index >= roots.size()) {
            return "";
        }
        return roots.get(index);
    }

    private String resolveColor(String key, int index) {
        String configured = categoryLabelResolver.resolveColor(key);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        if (!StringUtils.hasText(key)) {
            return PALETTE[index % PALETTE.length];
        }
        int hash = Math.abs(key.toLowerCase(Locale.ROOT).hashCode());
        return PALETTE[(hash + index) % PALETTE.length];
    }

    private static final class PathPair {
        private final String oldPath;
        private final String newPath;

        private PathPair(String oldPath, String newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
        }
    }
}
