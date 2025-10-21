package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents Git diff metadata for a file, including hunks and path changes.
 */
@JsonDeserialize(builder = IncrementalDiffGitDiffView.Builder.class)
public final class IncrementalDiffGitDiffView {

    private final String path;
    private final String previousPath;
    private final String changeType;
    private final boolean truncated;
    private final long totalBytes;
    private final int sameLineCount;
    private final int changedLineCount;
    private final int weightedBlockLineCount;
    private final Double fileSimilarity;
    private final List<IncrementalDiffGitHunkView> hunks;

    private IncrementalDiffGitDiffView(Builder builder) {
        this.path = builder.path;
        this.previousPath = builder.previousPath;
        this.changeType = builder.changeType;
        this.truncated = builder.truncated;
        this.totalBytes = Math.max(0L, builder.totalBytes);
        this.sameLineCount = Math.max(0, builder.sameLineCount);
        this.changedLineCount = Math.max(0, builder.changedLineCount);
        this.weightedBlockLineCount = Math.max(0, builder.weightedBlockLineCount);
        this.fileSimilarity = builder.fileSimilarity;
        this.hunks = builder.hunks == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.hunks));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPath() {
        return path;
    }

    public String getPreviousPath() {
        return previousPath;
    }

    public String getChangeType() {
        return changeType;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public int getSameLineCount() {
        return sameLineCount;
    }

    public int getChangedLineCount() {
        return changedLineCount;
    }

    public int getWeightedBlockLineCount() {
        return weightedBlockLineCount;
    }

    public Double getFileSimilarity() {
        return fileSimilarity;
    }

    public List<IncrementalDiffGitHunkView> getHunks() {
        return hunks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String path;
        private String previousPath;
        private String changeType;
        private boolean truncated;
        private long totalBytes;
        private int sameLineCount;
        private int changedLineCount;
        private int weightedBlockLineCount;
        private Double fileSimilarity;
        private List<IncrementalDiffGitHunkView> hunks;

        public Builder path(@JsonProperty("path") String path) {
            this.path = path;
            return this;
        }

        public Builder previousPath(@JsonProperty("previousPath") String previousPath) {
            this.previousPath = previousPath;
            return this;
        }

        public Builder changeType(@JsonProperty("changeType") String changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder truncated(@JsonProperty("truncated") boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public Builder totalBytes(@JsonProperty("totalBytes") long totalBytes) {
            this.totalBytes = totalBytes;
            return this;
        }

        public Builder sameLineCount(@JsonProperty("sameLineCount") int sameLineCount) {
            this.sameLineCount = sameLineCount;
            return this;
        }

        public Builder changedLineCount(@JsonProperty("changedLineCount") int changedLineCount) {
            this.changedLineCount = changedLineCount;
            return this;
        }

        public Builder weightedBlockLineCount(@JsonProperty("weightedBlockLineCount") int weightedBlockLineCount) {
            this.weightedBlockLineCount = weightedBlockLineCount;
            return this;
        }

        public Builder fileSimilarity(@JsonProperty("fileSimilarity") Double fileSimilarity) {
            this.fileSimilarity = fileSimilarity;
            return this;
        }

        public Builder hunks(@JsonProperty("hunks") List<IncrementalDiffGitHunkView> hunks) {
            this.hunks = hunks;
            return this;
        }

        public IncrementalDiffGitDiffView build() {
            return new IncrementalDiffGitDiffView(this);
        }
    }
}
