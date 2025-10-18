package com.example.codecompare.rebuild.repository.model;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 块级决策的持久化记录，包含状态、风险等级以及差异详情。
 */
@JsonDeserialize(builder = BlockDecisionRecord.Builder.class)
public final class BlockDecisionRecord {

    private final String id;
    private final String comparisonId;
    private final String filePath;
    private final String blockIdentifier;
    private final String status;
    private final String riskLevel;
    private final String action;
    private final Map<String, Object> metadata;
    private final BlockDiff diff;
    private final Instant analyzedAt;

    private BlockDecisionRecord(Builder builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.comparisonId = require(builder.comparisonId, "comparisonId");
        this.filePath = normalizePath(require(builder.filePath, "filePath"));
        this.blockIdentifier = builder.blockIdentifier;
        this.status = builder.status;
        this.riskLevel = builder.riskLevel;
        this.action = builder.action;
        this.metadata = builder.metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.diff = builder.diff;
        this.analyzedAt = builder.analyzedAt == null ? Instant.now() : builder.analyzedAt;
    }

    private <T> T require(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    private String normalizePath(String raw) {
        return raw.replace('\\', '/');
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getComparisonId() {
        return comparisonId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getBlockIdentifier() {
        return blockIdentifier;
    }

    public String getStatus() {
        return status;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getAction() {
        return action;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String id;
        private String comparisonId;
        private String filePath;
        private String blockIdentifier;
        private String status;
        private String riskLevel;
        private String action;
        private Map<String, Object> metadata;
        private BlockDiff diff;
        private Instant analyzedAt;

        public Builder() {
        }

        public Builder id(@JsonProperty("id") String id) {
            this.id = id;
            return this;
        }

        public Builder comparisonId(@JsonProperty("comparisonId") String comparisonId) {
            this.comparisonId = comparisonId;
            return this;
        }

        public Builder filePath(@JsonProperty("filePath") String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder blockIdentifier(@JsonProperty("blockIdentifier") String blockIdentifier) {
            this.blockIdentifier = blockIdentifier;
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

        public Builder action(@JsonProperty("action") String action) {
            this.action = action;
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

        public BlockDecisionRecord build() {
            return new BlockDecisionRecord(this);
        }
    }
}
