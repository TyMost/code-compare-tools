package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Basic representation of a single aligned diff row for UI consumption.
 */
@JsonDeserialize(builder = AlignedDiffLineView.Builder.class)
public final class AlignedDiffLineView {

    private final String type;
    private final Integer sourceLine;
    private final String sourceText;
    private final Integer targetLine;
    private final String targetText;

    private AlignedDiffLineView(Builder builder) {
        this.type = builder.type;
        this.sourceLine = builder.sourceLine;
        this.sourceText = builder.sourceText == null ? "" : builder.sourceText;
        this.targetLine = builder.targetLine;
        this.targetText = builder.targetText == null ? "" : builder.targetText;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getType() {
        return type;
    }

    public Integer getSourceLine() {
        return sourceLine;
    }

    public String getSourceText() {
        return sourceText;
    }

    public Integer getTargetLine() {
        return targetLine;
    }

    public String getTargetText() {
        return targetText;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String type;
        private Integer sourceLine;
        private String sourceText;
        private Integer targetLine;
        private String targetText;

        public Builder type(@JsonProperty("type") String type) {
            this.type = type;
            return this;
        }

        public Builder sourceLine(@JsonProperty("sourceLine") Integer sourceLine) {
            this.sourceLine = sourceLine;
            return this;
        }

        public Builder sourceText(@JsonProperty("sourceText") String sourceText) {
            this.sourceText = sourceText;
            return this;
        }

        public Builder targetLine(@JsonProperty("targetLine") Integer targetLine) {
            this.targetLine = targetLine;
            return this;
        }

        public Builder targetText(@JsonProperty("targetText") String targetText) {
            this.targetText = targetText;
            return this;
        }

        public AlignedDiffLineView build() {
            return new AlignedDiffLineView(this);
        }
    }
}
