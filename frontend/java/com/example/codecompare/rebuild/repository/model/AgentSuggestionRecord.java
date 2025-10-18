package com.example.codecompare.rebuild.repository.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent 建议的持久化对象，记录生成时间、置信度以及辅助证据。
 */
public final class AgentSuggestionRecord {

    private final String id;
    private final String comparisonId;
    private final String filePath;
    private final String decisionId;
    private final String blockIdentifier;
    private final String code;
    private final Double confidence;
    private final List<String> evidences;
    private final Instant createdAt;

    private AgentSuggestionRecord(Builder builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.comparisonId = Objects.requireNonNull(builder.comparisonId, "comparisonId must not be null");
        this.filePath = normalize(Objects.requireNonNull(builder.filePath, "filePath must not be null"));
        this.decisionId = Objects.requireNonNull(builder.decisionId, "decisionId must not be null");
        this.blockIdentifier = builder.blockIdentifier;
        this.code = builder.code;
        this.confidence = builder.confidence;
        this.evidences = builder.evidences == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(builder.evidences);
        this.createdAt = builder.createdAt == null ? Instant.now() : builder.createdAt;
    }

    private String normalize(String raw) {
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

    public String getDecisionId() {
        return decisionId;
    }

    public String getBlockIdentifier() {
        return blockIdentifier;
    }

    public String getCode() {
        return code;
    }

    public Double getConfidence() {
        return confidence;
    }

    public List<String> getEvidences() {
        return evidences;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static final class Builder {
        private String id;
        private String comparisonId;
        private String filePath;
        private String decisionId;
        private String blockIdentifier;
        private String code;
        private Double confidence;
        private List<String> evidences;
        private Instant createdAt;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder comparisonId(String comparisonId) {
            this.comparisonId = comparisonId;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder decisionId(String decisionId) {
            this.decisionId = decisionId;
            return this;
        }

        public Builder blockIdentifier(String blockIdentifier) {
            this.blockIdentifier = blockIdentifier;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder evidences(List<String> evidences) {
            this.evidences = evidences;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public AgentSuggestionRecord build() {
            return new AgentSuggestionRecord(this);
        }
    }
}
