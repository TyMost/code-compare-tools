package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detailed view for a single diff file, including block breakdown.
 */
@JsonDeserialize(builder = IncrementalDiffFileDetailView.Builder.class)
public final class IncrementalDiffFileDetailView {

    private final String projectCode;
    private final String filePath;
    private final List<IncrementalDiffGitSnapshotView> gitSnapshots;
    private final IncrementalDiffGitDiffView gitDiff;
    private final List<IncrementalDiffBlockView> blocks;
    private final Map<String, Integer> labelCounts;
    private final Map<String, Integer> lineCounts;
    private final int totalBlocks;
    private final int unlabeledBlocks;
    private final double averageSimilarity;
    private final int totalLineCount;
    private final Instant generatedAt;

    private IncrementalDiffFileDetailView(Builder builder) {
        this.projectCode = builder.projectCode;
        this.filePath = builder.filePath;
        this.gitSnapshots = builder.gitSnapshots == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.gitSnapshots));
        this.gitDiff = builder.gitDiff;
        this.blocks = builder.blocks == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.blocks));
        this.labelCounts = builder.labelCounts == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.labelCounts));
        this.lineCounts = builder.lineCounts == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.lineCounts));
        this.totalBlocks = builder.totalBlocks;
        this.unlabeledBlocks = builder.unlabeledBlocks;
        this.averageSimilarity = builder.averageSimilarity;
        this.totalLineCount = builder.totalLineCount;
        this.generatedAt = builder.generatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<IncrementalDiffGitSnapshotView> getGitSnapshots() {
        return gitSnapshots;
    }

    public IncrementalDiffGitDiffView getGitDiff() {
        return gitDiff;
    }

    public List<IncrementalDiffBlockView> getBlocks() {
        return blocks;
    }

    public Map<String, Integer> getLabelCounts() {
        return labelCounts;
    }

    public Map<String, Integer> getLineCounts() {
        return lineCounts;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getUnlabeledBlocks() {
        return unlabeledBlocks;
    }

    public double getAverageSimilarity() {
        return averageSimilarity;
    }

    public int getTotalLineCount() {
        return totalLineCount;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String projectCode;
        private String filePath;
        private List<IncrementalDiffGitSnapshotView> gitSnapshots;
        private IncrementalDiffGitDiffView gitDiff;
        private List<IncrementalDiffBlockView> blocks;
        private Map<String, Integer> labelCounts;
        private Map<String, Integer> lineCounts;
        private int totalBlocks;
        private int unlabeledBlocks;
        private double averageSimilarity;
        private int totalLineCount;
        private Instant generatedAt;

        public Builder projectCode(@JsonProperty("projectCode") String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder filePath(@JsonProperty("filePath") String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder gitSnapshots(@JsonProperty("gitSnapshots") List<IncrementalDiffGitSnapshotView> gitSnapshots) {
            this.gitSnapshots = gitSnapshots;
            return this;
        }

        public Builder gitDiff(@JsonProperty("gitDiff") IncrementalDiffGitDiffView gitDiff) {
            this.gitDiff = gitDiff;
            return this;
        }

        public Builder blocks(@JsonProperty("blocks") List<IncrementalDiffBlockView> blocks) {
            this.blocks = blocks;
            return this;
        }

        public Builder labelCounts(@JsonProperty("labelCounts") Map<String, Integer> labelCounts) {
            this.labelCounts = labelCounts;
            return this;
        }

        public Builder lineCounts(@JsonProperty("lineCounts") Map<String, Integer> lineCounts) {
            this.lineCounts = lineCounts;
            return this;
        }

        public Builder totalBlocks(@JsonProperty("totalBlocks") int totalBlocks) {
            this.totalBlocks = totalBlocks;
            return this;
        }

        public Builder unlabeledBlocks(@JsonProperty("unlabeledBlocks") int unlabeledBlocks) {
            this.unlabeledBlocks = unlabeledBlocks;
            return this;
        }

        public Builder averageSimilarity(@JsonProperty("averageSimilarity") double averageSimilarity) {
            this.averageSimilarity = averageSimilarity;
            return this;
        }

        public Builder totalLineCount(@JsonProperty("totalLineCount") int totalLineCount) {
            this.totalLineCount = totalLineCount;
            return this;
        }

        public Builder generatedAt(@JsonProperty("generatedAt") Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public IncrementalDiffFileDetailView build() {
            return new IncrementalDiffFileDetailView(this);
        }
    }
}
