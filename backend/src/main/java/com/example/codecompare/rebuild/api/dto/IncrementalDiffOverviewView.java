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
 * Overview payload for incremental diff module.
 */
@JsonDeserialize(builder = IncrementalDiffOverviewView.Builder.class)
public final class IncrementalDiffOverviewView {

    private final String projectCode;
    private final Instant generatedAt;
    private final Map<String, String> projectRoots;
    private final Map<String, String> baseCommits;
    private final Map<String, String> latestCommits;
    private final int filesChanged;
    private final int totalBlocks;
    private final int totalUnlabeledBlocks;
    private final double averageSimilarity;
    private final List<IncrementalDiffFileItemView> files;

    private IncrementalDiffOverviewView(Builder builder) {
        this.projectCode = builder.projectCode;
        this.generatedAt = builder.generatedAt;
        this.projectRoots = builder.projectRoots == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.projectRoots));
        this.baseCommits = builder.baseCommits == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.baseCommits));
        this.latestCommits = builder.latestCommits == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.latestCommits));
        this.filesChanged = builder.filesChanged;
        this.totalBlocks = builder.totalBlocks;
        this.totalUnlabeledBlocks = builder.totalUnlabeledBlocks;
        this.averageSimilarity = builder.averageSimilarity;
        this.files = builder.files == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.files));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProjectCode() {
        return projectCode;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Map<String, String> getProjectRoots() {
        return projectRoots;
    }

    public Map<String, String> getBaseCommits() {
        return baseCommits;
    }

    public Map<String, String> getLatestCommits() {
        return latestCommits;
    }

    public int getFilesChanged() {
        return filesChanged;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getTotalUnlabeledBlocks() {
        return totalUnlabeledBlocks;
    }

    public double getAverageSimilarity() {
        return averageSimilarity;
    }

    public List<IncrementalDiffFileItemView> getFiles() {
        return files;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String projectCode;
        private Instant generatedAt;
        private Map<String, String> projectRoots;
        private Map<String, String> baseCommits;
        private Map<String, String> latestCommits;
        private int filesChanged;
        private int totalBlocks;
        private int totalUnlabeledBlocks;
        private double averageSimilarity;
        private List<IncrementalDiffFileItemView> files;

        public Builder projectCode(@JsonProperty("projectCode") String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder generatedAt(@JsonProperty("generatedAt") Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder projectRoots(@JsonProperty("projectRoots") Map<String, String> projectRoots) {
            this.projectRoots = projectRoots;
            return this;
        }

        public Builder baseCommits(@JsonProperty("baseCommits") Map<String, String> baseCommits) {
            this.baseCommits = baseCommits;
            return this;
        }

        public Builder latestCommits(@JsonProperty("latestCommits") Map<String, String> latestCommits) {
            this.latestCommits = latestCommits;
            return this;
        }

        public Builder filesChanged(@JsonProperty("filesChanged") int filesChanged) {
            this.filesChanged = filesChanged;
            return this;
        }

        public Builder totalBlocks(@JsonProperty("totalBlocks") int totalBlocks) {
            this.totalBlocks = totalBlocks;
            return this;
        }

        public Builder totalUnlabeledBlocks(@JsonProperty("totalUnlabeledBlocks") int totalUnlabeledBlocks) {
            this.totalUnlabeledBlocks = totalUnlabeledBlocks;
            return this;
        }

        public Builder averageSimilarity(@JsonProperty("averageSimilarity") double averageSimilarity) {
            this.averageSimilarity = averageSimilarity;
            return this;
        }

        public Builder files(@JsonProperty("files") List<IncrementalDiffFileItemView> files) {
            this.files = files;
            return this;
        }

        public IncrementalDiffOverviewView build() {
            return new IncrementalDiffOverviewView(this);
        }
    }
}
