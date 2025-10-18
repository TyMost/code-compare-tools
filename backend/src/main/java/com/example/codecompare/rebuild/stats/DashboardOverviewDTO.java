package com.example.codecompare.rebuild.stats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 仪表盘概览数据结构。
 */
public final class DashboardOverviewDTO {

    private final String projectCode;
    private final String oldProjectPath;
    private final String newProjectPath;
    private final Instant lastSyncedAt;
    private final List<CategoryStatDTO> codeCategoryStats;
    private final double newCodeRatio;
    private final long totalLines;
    private final long totalBlocks;

    private DashboardOverviewDTO(Builder builder) {
        this.projectCode = builder.projectCode;
        this.oldProjectPath = builder.oldProjectPath;
        this.newProjectPath = builder.newProjectPath;
        this.lastSyncedAt = builder.lastSyncedAt;
        this.codeCategoryStats = Collections.unmodifiableList(new ArrayList<>(builder.codeCategoryStats));
        this.newCodeRatio = builder.newCodeRatio;
        this.totalLines = builder.totalLines;
        this.totalBlocks = builder.totalBlocks;
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String projectCode;
        private String oldProjectPath;
        private String newProjectPath;
        private Instant lastSyncedAt;
        private final List<CategoryStatDTO> codeCategoryStats = new ArrayList<>();
        private double newCodeRatio;
        private long totalLines;
        private long totalBlocks;

        public Builder projectCode(String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder oldProjectPath(String oldProjectPath) {
            this.oldProjectPath = oldProjectPath;
            return this;
        }

        public Builder newProjectPath(String newProjectPath) {
            this.newProjectPath = newProjectPath;
            return this;
        }

        public Builder lastSyncedAt(Instant lastSyncedAt) {
            this.lastSyncedAt = lastSyncedAt;
            return this;
        }

        public Builder addCategoryStat(CategoryStatDTO stat) {
            if (stat != null) {
                this.codeCategoryStats.add(stat);
            }
            return this;
        }

        public Builder categoryStats(List<CategoryStatDTO> stats) {
            this.codeCategoryStats.clear();
            if (stats != null) {
                this.codeCategoryStats.addAll(stats);
            }
            return this;
        }

        public Builder newCodeRatio(double newCodeRatio) {
            this.newCodeRatio = newCodeRatio;
            return this;
        }

        public Builder totalLines(long totalLines) {
            this.totalLines = totalLines;
            return this;
        }

        public Builder totalBlocks(long totalBlocks) {
            this.totalBlocks = totalBlocks;
            return this;
        }

        public DashboardOverviewDTO build() {
            return new DashboardOverviewDTO(this);
        }
    }
}
