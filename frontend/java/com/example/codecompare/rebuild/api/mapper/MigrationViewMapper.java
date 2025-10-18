package com.example.codecompare.rebuild.api.mapper;

import com.example.codecompare.rebuild.api.dto.AgentSuggestionView;
import com.example.codecompare.rebuild.api.dto.CodeBlockDetailView;
import com.example.codecompare.rebuild.api.dto.CodeBlockItemView;
import com.example.codecompare.rebuild.api.dto.CodeBlockListView;
import com.example.codecompare.rebuild.api.dto.ConfigurationSyncItemView;
import com.example.codecompare.rebuild.api.dto.ConfigurationSyncView;
import com.example.codecompare.rebuild.api.dto.MigrationOverviewView;
import com.example.codecompare.rebuild.core.config.ConfigurationReloadReport;
import com.example.codecompare.rebuild.stats.BlockStatsResponseDTO;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import com.example.codecompare.rebuild.stats.CodeBlockItemDTO;
import com.example.codecompare.rebuild.stats.DashboardOverviewDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API 层视图映射器，负责将内部 DTO 转换为前端所需结构。
 */
@Component
public class MigrationViewMapper {

    public MigrationOverviewView toOverviewView(DashboardOverviewDTO source) {
        return toOverviewView(source, null);
    }

    public MigrationOverviewView toOverviewView(DashboardOverviewDTO source,
                                                ConfigurationReloadReport configReport) {
        if (source == null) {
            return new MigrationOverviewView(null, null, null, null, Collections.emptyList(), 0d, 0L, 0L, toConfigSyncView(configReport));
        }
        return new MigrationOverviewView(
                source.getProjectCode(),
                source.getOldProjectPath(),
                source.getNewProjectPath(),
                source.getLastSyncedAt(),
                source.getCodeCategoryStats(),
                source.getNewCodeRatio(),
                source.getTotalLines(),
                source.getTotalBlocks(),
                toConfigSyncView(configReport)
        );
    }

    public CodeBlockListView toBlockListView(BlockStatsResponseDTO source) {
        if (source == null) {
            return new CodeBlockListView(Collections.emptyList(), 1, 20, 0L, 0,
                    Collections.emptyList(), 0d, 0L);
        }
        List<CodeBlockItemView> items = CollectionUtils.isEmpty(source.getData())
                ? Collections.emptyList()
                : source.getData().stream()
                .map(this::toItemView)
                .collect(Collectors.toList());
        return new CodeBlockListView(
                items,
                source.getPage(),
                source.getSize(),
                source.getTotal(),
                source.getTotalPages(),
                source.getCategoryOptions(),
                source.getNewCodeRatio(),
                source.getTotalLines()
        );
    }

    public CodeBlockDetailView toDetailView(CodeBlockDetailDTO source) {
        if (source == null) {
            return null;
        }
        return new CodeBlockDetailView(
                source.getId(),
                source.getComparisonId(),
                source.getSourceProjectCode(),
                source.getTargetProjectCode(),
                source.getFilePath(),
                source.getStartLine(),
                source.getEndLine(),
                source.getOldCode(),
                source.getNewCode(),
                source.getStatus(),
                source.getStatusLabel(),
                source.getCategoryKeys(),
                source.isAiSuggestionEnabled()
        );
    }

    public AgentSuggestionView toAgentSuggestionView(String blockId, String message) {
        return new AgentSuggestionView(
                blockId,
                false,
                null,
                null,
                null,
                null,
                message
        );
    }

    private CodeBlockItemView toItemView(CodeBlockItemDTO item) {
        return new CodeBlockItemView(
                item.getId(),
                item.getFilePath(),
                item.getStartLine(),
                item.getEndLine(),
                item.getCodeSnippet(),
                item.getCategories(),
                item.getStatus(),
                item.getStatusLabel()
        );
    }

    private ConfigurationSyncView toConfigSyncView(ConfigurationReloadReport report) {
        if (report == null) {
            return null;
        }
        List<ConfigurationReloadReport.Item> items = report.getItems();
        List<ConfigurationSyncItemView> views;
        if (items == null || items.isEmpty()) {
            views = Collections.emptyList();
        } else {
            views = items.stream()
                    .map(this::toConfigSyncItemView)
                    .collect(Collectors.toList());
        }
        return new ConfigurationSyncView(report.getTriggeredAt(), report.getCompletedAt(), report.isSuccess(), views);
    }

    private ConfigurationSyncItemView toConfigSyncItemView(ConfigurationReloadReport.Item item) {
        return new ConfigurationSyncItemView(
                item.getName(),
                item.isSuccess(),
                item.isUpdated(),
                item.getMessage(),
                item.getLoadedAt(),
                item.getSource(),
                item.getCount()
        );
    }
}
