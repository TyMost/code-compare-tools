package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Represents a single file comparison across source and target projects.
 */
@JsonDeserialize(builder = GitComparisonFileView.Builder.class)
public final class GitComparisonFileView {

    private final String filePath;
    private final IncrementalDiffFileDetailView source;
    private final IncrementalDiffFileDetailView target;
    private final DualIncrementalComparisonView dualComparison;

    private GitComparisonFileView(Builder builder) {
        this.filePath = builder.filePath;
        this.source = builder.source;
        this.target = builder.target;
        this.dualComparison = builder.dualComparison;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFilePath() {
        return filePath;
    }

    public IncrementalDiffFileDetailView getSource() {
        return source;
    }

    public IncrementalDiffFileDetailView getTarget() {
        return target;
    }

    public DualIncrementalComparisonView getDualComparison() {
        return dualComparison;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String filePath;
        private IncrementalDiffFileDetailView source;
        private IncrementalDiffFileDetailView target;
        private DualIncrementalComparisonView dualComparison;

        public Builder filePath(@JsonProperty("filePath") String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder source(@JsonProperty("source") IncrementalDiffFileDetailView source) {
            this.source = source;
            return this;
        }

        public Builder target(@JsonProperty("target") IncrementalDiffFileDetailView target) {
            this.target = target;
            return this;
        }

        public Builder dualComparison(@JsonProperty("dualComparison") DualIncrementalComparisonView dualComparison) {
            this.dualComparison = dualComparison;
            return this;
        }

        public GitComparisonFileView build() {
            return new GitComparisonFileView(this);
        }
    }
}
