package com.example.codecompare.rebuild.scanning;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 扫描结果的摘要信息，提供项目级别的扫描耗时、文件数量等指标。
 */
public final class ScanSummary {

    private final String projectCode;
    private final List<String> scannedRoots;
    private final int filesScanned;
    private final long totalBytes;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Duration duration;
    private final List<String> warnings;

    private ScanSummary(Builder builder) {
        this.projectCode = Objects.requireNonNull(builder.projectCode, "projectCode must not be null");
        this.scannedRoots = Collections.unmodifiableList(new ArrayList<>(builder.scannedRoots));
        this.filesScanned = builder.filesScanned;
        this.totalBytes = builder.totalBytes;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.duration = builder.duration == null && builder.startedAt != null && builder.completedAt != null
                ? Duration.between(builder.startedAt, builder.completedAt)
                : builder.duration;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
    }

    public String getProjectCode() {
        return projectCode;
    }

    public List<String> getScannedRoots() {
        return scannedRoots;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Duration getDuration() {
        return duration;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public double getThroughputPerSecond() {
        if (duration == null || duration.isZero()) {
            return 0d;
        }
        return filesScanned / (duration.toMillis() / 1000.0);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public static ScanSummary empty(String projectCode, Instant timestamp) {
        return ScanSummary.builder()
                .projectCode(projectCode)
                .startedAt(timestamp)
                .completedAt(timestamp)
                .build();
    }

    public static final class Builder {

        private String projectCode;
        private final List<String> scannedRoots = new ArrayList<>();
        private int filesScanned;
        private long totalBytes;
        private Instant startedAt;
        private Instant completedAt;
        private Duration duration;
        private final List<String> warnings = new ArrayList<>();

        private Builder() {
        }

        private Builder from(ScanSummary summary) {
            this.projectCode = summary.projectCode;
            this.scannedRoots.addAll(summary.scannedRoots);
            this.filesScanned = summary.filesScanned;
            this.totalBytes = summary.totalBytes;
            this.startedAt = summary.startedAt;
            this.completedAt = summary.completedAt;
            this.duration = summary.duration;
            this.warnings.addAll(summary.warnings);
            return this;
        }

        public Builder projectCode(String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder addScannedRoot(String root) {
            if (root != null && !root.trim().isEmpty()) {
                this.scannedRoots.add(root);
            }
            return this;
        }

        public Builder scannedRoots(List<String> roots) {
            this.scannedRoots.clear();
            if (roots != null) {
                roots.forEach(this::addScannedRoot);
            }
            return this;
        }

        public Builder filesScanned(int filesScanned) {
            this.filesScanned = filesScanned;
            return this;
        }

        public Builder totalBytes(long totalBytes) {
            this.totalBytes = totalBytes;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder addWarning(String warning) {
            if (warning != null && !warning.trim().isEmpty()) {
                this.warnings.add(warning);
            }
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings.clear();
            if (warnings != null) {
                warnings.forEach(this::addWarning);
            }
            return this;
        }

        public ScanSummary build() {
            return new ScanSummary(this);
        }
    }
}
