package com.example.codecompare.rebuild.scanning;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, String> baseCommits;
    private final Map<String, String> latestCommits;
    private final String diffEngine;
    private final DiffConfiguration diffConfiguration;

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
        this.baseCommits = Collections.unmodifiableMap(new LinkedHashMap<>(builder.baseCommits));
        this.latestCommits = Collections.unmodifiableMap(new LinkedHashMap<>(builder.latestCommits));
        this.diffEngine = builder.diffEngine == null || builder.diffEngine.trim().isEmpty()
                ? "default"
                : builder.diffEngine.trim();
        this.diffConfiguration = builder.diffConfiguration == null
                ? DiffConfiguration.empty()
                : builder.diffConfiguration;
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

    public Map<String, String> getBaseCommits() {
        return baseCommits;
    }

    public Map<String, String> getLatestCommits() {
        return latestCommits;
    }

    public String getDiffEngine() {
        return diffEngine;
    }

    public DiffConfiguration getDiffConfiguration() {
        return diffConfiguration;
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
                .baseCommits(Collections.emptyMap())
                .latestCommits(Collections.emptyMap())
                .build();
    }

    public static final class DiffConfiguration {

        private final boolean gitIncludeRenames;
        private final boolean gitDetectCopies;
        private final long gitMaxDiffBytes;
        private final long gitMaxFileSizeBytes;

        private DiffConfiguration(Builder builder) {
            this.gitIncludeRenames = builder.gitIncludeRenames;
            this.gitDetectCopies = builder.gitDetectCopies;
            this.gitMaxDiffBytes = builder.gitMaxDiffBytes;
            this.gitMaxFileSizeBytes = builder.gitMaxFileSizeBytes;
        }

        public boolean isGitIncludeRenames() {
            return gitIncludeRenames;
        }

        public boolean isGitDetectCopies() {
            return gitDetectCopies;
        }

        public long getGitMaxDiffBytes() {
            return gitMaxDiffBytes;
        }

        public long getGitMaxFileSizeBytes() {
            return gitMaxFileSizeBytes;
        }

        public Builder toBuilder() {
            return new Builder().from(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static DiffConfiguration empty() {
            return builder().build();
        }

        public static final class Builder {
            private boolean gitIncludeRenames;
            private boolean gitDetectCopies;
            private long gitMaxDiffBytes;
            private long gitMaxFileSizeBytes;

            private Builder() {
            }

            private Builder from(DiffConfiguration configuration) {
                this.gitIncludeRenames = configuration.gitIncludeRenames;
                this.gitDetectCopies = configuration.gitDetectCopies;
                this.gitMaxDiffBytes = configuration.gitMaxDiffBytes;
                this.gitMaxFileSizeBytes = configuration.gitMaxFileSizeBytes;
                return this;
            }

            public Builder gitIncludeRenames(boolean gitIncludeRenames) {
                this.gitIncludeRenames = gitIncludeRenames;
                return this;
            }

            public Builder gitDetectCopies(boolean gitDetectCopies) {
                this.gitDetectCopies = gitDetectCopies;
                return this;
            }

            public Builder gitMaxDiffBytes(long gitMaxDiffBytes) {
                this.gitMaxDiffBytes = Math.max(gitMaxDiffBytes, 0);
                return this;
            }

            public Builder gitMaxFileSizeBytes(long gitMaxFileSizeBytes) {
                this.gitMaxFileSizeBytes = Math.max(gitMaxFileSizeBytes, 0);
                return this;
            }

            public DiffConfiguration build() {
                return new DiffConfiguration(this);
            }
        }
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
        private final Map<String, String> baseCommits = new LinkedHashMap<>();
        private final Map<String, String> latestCommits = new LinkedHashMap<>();
        private String diffEngine = "default";
        private DiffConfiguration diffConfiguration = DiffConfiguration.empty();

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
            this.baseCommits.clear();
            this.baseCommits.putAll(summary.baseCommits);
            this.latestCommits.clear();
            this.latestCommits.putAll(summary.latestCommits);
            this.diffEngine = summary.diffEngine;
            this.diffConfiguration = summary.diffConfiguration;
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

        public Builder putBaseCommit(String key, String value) {
            if (key != null && value != null) {
                this.baseCommits.put(key, value);
            }
            return this;
        }

        public Builder baseCommits(Map<String, String> values) {
            this.baseCommits.clear();
            if (values != null) {
                values.forEach(this::putBaseCommit);
            }
            return this;
        }

        public Builder putLatestCommit(String key, String value) {
            if (key != null && value != null) {
                this.latestCommits.put(key, value);
            }
            return this;
        }

        public Builder latestCommits(Map<String, String> values) {
            this.latestCommits.clear();
            if (values != null) {
                values.forEach(this::putLatestCommit);
            }
            return this;
        }

        public Builder diffEngine(String diffEngine) {
            this.diffEngine = diffEngine == null || diffEngine.trim().isEmpty() ? "default" : diffEngine.trim();
            return this;
        }

        public Builder diffConfiguration(DiffConfiguration diffConfiguration) {
            this.diffConfiguration = diffConfiguration == null ? DiffConfiguration.empty() : diffConfiguration;
            return this;
        }

        public ScanSummary build() {
            return new ScanSummary(this);
        }
    }
}
