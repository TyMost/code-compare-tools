package com.example.codecompare.rebuild.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 提供块级决策的完整快照数据结构。
 */
@JsonDeserialize(builder = BlockDecisionSnapshot.Builder.class)
public final class BlockDecisionSnapshot {

    private final String id;
    private final String comparisonId;
    private final String filePath;
    private final String sourceProjectCode;
    private final String targetProjectCode;
    private final Instant analyzedAt;
    private final List<BlockDecisionRecord> records;

    private BlockDecisionSnapshot(Builder builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.comparisonId = require(builder.comparisonId, "comparisonId");
        this.filePath = normalize(require(builder.filePath, "filePath"));
        this.sourceProjectCode = require(builder.sourceProjectCode, "sourceProjectCode");
        this.targetProjectCode = require(builder.targetProjectCode, "targetProjectCode");
        this.analyzedAt = builder.analyzedAt == null ? Instant.now() : builder.analyzedAt;
        this.records = builder.records == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.records));
    }

    private <T> T require(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    private String normalize(String path) {
        return path.replace('\\', '/');
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

    public String getSourceProjectCode() {
        return sourceProjectCode;
    }

    public String getTargetProjectCode() {
        return targetProjectCode;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public List<BlockDecisionRecord> getRecords() {
        return records;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String id;
        private String comparisonId;
        private String filePath;
        private String sourceProjectCode;
        private String targetProjectCode;
        private Instant analyzedAt;
        private List<BlockDecisionRecord> records;

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

        public Builder sourceProjectCode(@JsonProperty("sourceProjectCode") String sourceProjectCode) {
            this.sourceProjectCode = sourceProjectCode;
            return this;
        }

        public Builder targetProjectCode(@JsonProperty("targetProjectCode") String targetProjectCode) {
            this.targetProjectCode = targetProjectCode;
            return this;
        }

        public Builder analyzedAt(@JsonProperty("analyzedAt") Instant analyzedAt) {
            this.analyzedAt = analyzedAt;
            return this;
        }

        public Builder records(@JsonProperty("records") List<BlockDecisionRecord> records) {
            this.records = records;
            return this;
        }

        public BlockDecisionSnapshot build() {
            return new BlockDecisionSnapshot(this);
        }
    }
}
