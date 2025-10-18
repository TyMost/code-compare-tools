package com.example.codecompare.rebuild.diff.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * 差异结果汇总指标。
 */
@JsonDeserialize(builder = DiffSummary.Builder.class)
public final class DiffSummary {

    private final int insertSegments;
    private final int deleteSegments;
    private final int changeSegments;
    private final int totalChangedLines;
    private final int maxContinuousChangedLines;

    private DiffSummary(Builder builder) {
        this.insertSegments = builder.insertSegments;
        this.deleteSegments = builder.deleteSegments;
        this.changeSegments = builder.changeSegments;
        this.totalChangedLines = builder.totalChangedLines;
        this.maxContinuousChangedLines = builder.maxContinuousChangedLines;
    }

    public int getInsertSegments() {
        return insertSegments;
    }

    public int getDeleteSegments() {
        return deleteSegments;
    }

    public int getChangeSegments() {
        return changeSegments;
    }

    public int getTotalChangedLines() {
        return totalChangedLines;
    }

    public int getMaxContinuousChangedLines() {
        return maxContinuousChangedLines;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private int insertSegments;
        private int deleteSegments;
        private int changeSegments;
        private int totalChangedLines;
        private int maxContinuousChangedLines;

        public Builder() {
        }

        public Builder insertSegments(@JsonProperty("insertSegments") int insertSegments) {
            this.insertSegments = insertSegments;
            return this;
        }

        public Builder deleteSegments(@JsonProperty("deleteSegments") int deleteSegments) {
            this.deleteSegments = deleteSegments;
            return this;
        }

        public Builder changeSegments(@JsonProperty("changeSegments") int changeSegments) {
            this.changeSegments = changeSegments;
            return this;
        }

        public Builder totalChangedLines(@JsonProperty("totalChangedLines") int totalChangedLines) {
            this.totalChangedLines = totalChangedLines;
            return this;
        }

        public Builder maxContinuousChangedLines(@JsonProperty("maxContinuousChangedLines") int maxContinuousChangedLines) {
            this.maxContinuousChangedLines = maxContinuousChangedLines;
            return this;
        }

        public DiffSummary build() {
            return new DiffSummary(this);
        }
    }
}
