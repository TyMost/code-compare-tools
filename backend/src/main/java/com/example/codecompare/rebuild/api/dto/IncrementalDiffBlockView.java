package com.example.codecompare.rebuild.api.dto;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single diff block enriched with metadata and status information.
 */
@JsonDeserialize(builder = IncrementalDiffBlockView.Builder.class)
public final class IncrementalDiffBlockView {

    private final String blockId;
    private final String status;
    private final String riskLevel;
    private final Map<String, Object> metadata;
    private final BlockDiff diff;
    private final Instant analyzedAt;
    private final List<AlignedDiffLineView> alignedLines;

    private IncrementalDiffBlockView(Builder builder) {
        this.blockId = builder.blockId;
        this.status = builder.status;
        this.riskLevel = builder.riskLevel;
        this.metadata = builder.metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.diff = builder.diff;
        this.analyzedAt = builder.analyzedAt;
        this.alignedLines = builder.alignedLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.alignedLines));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getBlockId() {
        return blockId;
    }

    public String getStatus() {
        return status;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public BlockDiff getDiff() {
        return diff;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public List<AlignedDiffLineView> getAlignedLines() {
        return alignedLines;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String blockId;
        private String status;
        private String riskLevel;
        private Map<String, Object> metadata;
        private BlockDiff diff;
        private Instant analyzedAt;
        private List<AlignedDiffLineView> alignedLines;

        public Builder blockId(@JsonProperty("blockId") String blockId) {
            this.blockId = blockId;
            return this;
        }

        public Builder status(@JsonProperty("status") String status) {
            this.status = status;
            return this;
        }

        public Builder riskLevel(@JsonProperty("riskLevel") String riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder metadata(@JsonProperty("metadata") Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder diff(@JsonProperty("diff") BlockDiff diff) {
            this.diff = diff;
            return this;
        }

        public Builder analyzedAt(@JsonProperty("analyzedAt") Instant analyzedAt) {
            this.analyzedAt = analyzedAt;
            return this;
        }

        public Builder alignedLines(@JsonProperty("alignedLines") List<AlignedDiffLineView> alignedLines) {
            this.alignedLines = alignedLines;
            return this;
        }

        public IncrementalDiffBlockView build() {
            return new IncrementalDiffBlockView(this);
        }
    }
}
