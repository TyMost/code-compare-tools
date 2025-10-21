package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Represents a paired incremental diff block comparison between source and target projects.
 */
@JsonDeserialize(builder = DualIncrementalComparisonBlockView.Builder.class)
public final class DualIncrementalComparisonBlockView {

    private final int index;
    private final double similarity;
    private final int sourceChangedLines;
    private final int targetChangedLines;
    private final int referenceLineCount;
    private final boolean sourceOnly;
    private final boolean targetOnly;

    private DualIncrementalComparisonBlockView(Builder builder) {
        this.index = Math.max(0, builder.index);
        this.similarity = builder.similarity < 0d ? 0d : Math.min(builder.similarity, 100d);
        this.sourceChangedLines = Math.max(0, builder.sourceChangedLines);
        this.targetChangedLines = Math.max(0, builder.targetChangedLines);
        this.referenceLineCount = Math.max(0, builder.referenceLineCount);
        this.sourceOnly = builder.sourceOnly;
        this.targetOnly = builder.targetOnly;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getIndex() {
        return index;
    }

    public double getSimilarity() {
        return similarity;
    }

    public int getSourceChangedLines() {
        return sourceChangedLines;
    }

    public int getTargetChangedLines() {
        return targetChangedLines;
    }

    public int getReferenceLineCount() {
        return referenceLineCount;
    }

    public boolean isSourceOnly() {
        return sourceOnly;
    }

    public boolean isTargetOnly() {
        return targetOnly;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private int index;
        private double similarity;
        private int sourceChangedLines;
        private int targetChangedLines;
        private int referenceLineCount;
        private boolean sourceOnly;
        private boolean targetOnly;

        public Builder() {
        }

        public Builder index(@JsonProperty("index") int index) {
            this.index = index;
            return this;
        }

        public Builder similarity(@JsonProperty("similarity") double similarity) {
            this.similarity = similarity;
            return this;
        }

        public Builder sourceChangedLines(@JsonProperty("sourceChangedLines") int sourceChangedLines) {
            this.sourceChangedLines = sourceChangedLines;
            return this;
        }

        public Builder targetChangedLines(@JsonProperty("targetChangedLines") int targetChangedLines) {
            this.targetChangedLines = targetChangedLines;
            return this;
        }

        public Builder referenceLineCount(@JsonProperty("referenceLineCount") int referenceLineCount) {
            this.referenceLineCount = referenceLineCount;
            return this;
        }

        public Builder sourceOnly(@JsonProperty("sourceOnly") boolean sourceOnly) {
            this.sourceOnly = sourceOnly;
            return this;
        }

        public Builder targetOnly(@JsonProperty("targetOnly") boolean targetOnly) {
            this.targetOnly = targetOnly;
            return this;
        }

        public DualIncrementalComparisonBlockView build() {
            return new DualIncrementalComparisonBlockView(this);
        }
    }
}
