package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated similarity view comparing incremental diffs from source and target projects.
 */
@JsonDeserialize(builder = DualIncrementalComparisonView.Builder.class)
public final class DualIncrementalComparisonView {

    private final double fileSimilarity;
    private final int sameLineCount;
    private final int totalChangedLines;
    private final List<DualIncrementalComparisonBlockView> blocks;
    private final double coverageAtoB;
    private final double coverageBtoA;

    private DualIncrementalComparisonView(Builder builder) {
        this.fileSimilarity = builder.fileSimilarity < 0d ? 0d : Math.min(builder.fileSimilarity, 100d);
        this.sameLineCount = Math.max(0, builder.sameLineCount);
        this.totalChangedLines = Math.max(0, builder.totalChangedLines);
        this.blocks = builder.blocks == null
                ? Collections.<DualIncrementalComparisonBlockView>emptyList()
                : Collections.unmodifiableList(new ArrayList<DualIncrementalComparisonBlockView>(builder.blocks));
        this.coverageAtoB = clamp(builder.coverageAtoB);
        this.coverageBtoA = clamp(builder.coverageBtoA);
    }

    public static Builder builder() {
        return new Builder();
    }

    public double getFileSimilarity() {
        return fileSimilarity;
    }

    public int getSameLineCount() {
        return sameLineCount;
    }

    public int getTotalChangedLines() {
        return totalChangedLines;
    }

    public List<DualIncrementalComparisonBlockView> getBlocks() {
        return blocks;
    }

    public double getCoverageAtoB() {
        return coverageAtoB;
    }

    public double getCoverageBtoA() {
        return coverageBtoA;
    }

    private double clamp(double value) {
        if (value < 0d) {
            return 0d;
        }
        if (value > 100d) {
            return 100d;
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private double fileSimilarity;
        private int sameLineCount;
        private int totalChangedLines;
        private List<DualIncrementalComparisonBlockView> blocks;
        private double coverageAtoB;
        private double coverageBtoA;

        public Builder() {
        }

        public Builder fileSimilarity(@JsonProperty("fileSimilarity") double fileSimilarity) {
            this.fileSimilarity = fileSimilarity;
            return this;
        }

        public Builder sameLineCount(@JsonProperty("sameLineCount") int sameLineCount) {
            this.sameLineCount = sameLineCount;
            return this;
        }

        public Builder totalChangedLines(@JsonProperty("totalChangedLines") int totalChangedLines) {
            this.totalChangedLines = totalChangedLines;
            return this;
        }

        public Builder blocks(@JsonProperty("blocks") List<DualIncrementalComparisonBlockView> blocks) {
            this.blocks = blocks;
            return this;
        }

        public Builder coverageAtoB(@JsonProperty("coverageAtoB") double coverageAtoB) {
            this.coverageAtoB = coverageAtoB;
            return this;
        }

        public Builder coverageBtoA(@JsonProperty("coverageBtoA") double coverageBtoA) {
            this.coverageBtoA = coverageBtoA;
            return this;
        }

        public DualIncrementalComparisonView build() {
            return new DualIncrementalComparisonView(this);
        }
    }
}
