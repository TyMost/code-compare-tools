package com.example.codecompare.rebuild.api.dto;

import com.example.codecompare.rebuild.stats.CategoryStatDTO;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 仪表盘概览前端视图。
 */
public class MigrationOverviewView {

    private final String projectCode;
    private final String oldProjectPath;
    private final String newProjectPath;
    private final Instant lastSyncedAt;
    private final List<CategoryStatDTO> codeCategoryStats;
    private final double newCodeRatio;
    private final long totalLines;
    private final long totalBlocks;
    private final ConfigurationSyncView configSync;

    public MigrationOverviewView(String projectCode,
                                 String oldProjectPath,
                                 String newProjectPath,
                                 Instant lastSyncedAt,
                                 List<CategoryStatDTO> codeCategoryStats,
                                 double newCodeRatio,
                                 long totalLines,
                                 long totalBlocks) {
        this(projectCode,
                oldProjectPath,
                newProjectPath,
                lastSyncedAt,
                codeCategoryStats,
                newCodeRatio,
                totalLines,
                totalBlocks,
                null);
    }

    public MigrationOverviewView(String projectCode,
                                 String oldProjectPath,
                                 String newProjectPath,
                                 Instant lastSyncedAt,
                                 List<CategoryStatDTO> codeCategoryStats,
                                 double newCodeRatio,
                                 long totalLines,
                                 long totalBlocks,
                                 ConfigurationSyncView configSync) {
        this.projectCode = projectCode;
        this.oldProjectPath = oldProjectPath;
        this.newProjectPath = newProjectPath;
        this.lastSyncedAt = lastSyncedAt;
        this.codeCategoryStats = codeCategoryStats == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(codeCategoryStats);
        this.newCodeRatio = newCodeRatio;
        this.totalLines = totalLines;
        this.totalBlocks = totalBlocks;
        this.configSync = configSync;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getOldProjectPath() {
        return oldProjectPath;
    }

    public String getNewProjectPath() {
        return newProjectPath;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public List<CategoryStatDTO> getCodeCategoryStats() {
        return codeCategoryStats;
    }

    public double getNewCodeRatio() {
        return newCodeRatio;
    }

    public long getTotalLines() {
        return totalLines;
    }

    public long getTotalBlocks() {
        return totalBlocks;
    }

    public ConfigurationSyncView getConfigSync() {
        return configSync;
    }
}
