package com.example.codecompare.rebuild.agent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 建议结果，供前端展示或后续缓存存储。
 */
public final class AgentSuggestion {

    private final String blockId;
    private final String suggestion;
    private final AgentSuggestionStatus status;
    private final Instant generatedAt;
    private final Map<String, Object> metadata;

    public AgentSuggestion(String blockId,
                           String suggestion,
                           AgentSuggestionStatus status,
                           Instant generatedAt) {
        this(blockId, suggestion, status, generatedAt, null);
    }

    public AgentSuggestion(String blockId,
                           String suggestion,
                           AgentSuggestionStatus status,
                           Instant generatedAt,
                           Map<String, Object> metadata) {
        this.blockId = blockId;
        this.suggestion = suggestion;
        this.status = status;
        this.generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        if (metadata == null || metadata.isEmpty()) {
            this.metadata = Collections.emptyMap();
        } else {
            this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }

    public String getBlockId() {
        return blockId;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public AgentSuggestionStatus getStatus() {
        return status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public AgentSuggestion withGeneratedAt(Instant value) {
        return new AgentSuggestion(blockId, suggestion, status, value, metadata);
    }

    public AgentSuggestion withStatus(AgentSuggestionStatus value) {
        return new AgentSuggestion(blockId, suggestion, value, generatedAt, metadata);
    }

    public AgentSuggestion withMetadata(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            if (metadata.isEmpty()) {
                return this;
            }
            return new AgentSuggestion(blockId, suggestion, status, generatedAt, Collections.emptyMap());
        }
        if (metadata.equals(value)) {
            return this;
        }
        return new AgentSuggestion(blockId, suggestion, status, generatedAt, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentSuggestion)) {
            return false;
        }
        AgentSuggestion that = (AgentSuggestion) o;
        return Objects.equals(blockId, that.blockId)
                && Objects.equals(suggestion, that.suggestion)
                && status == that.status
                && Objects.equals(generatedAt, that.generatedAt)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, suggestion, status, generatedAt, metadata);
    }

    @Override
    public String toString() {
        return "AgentSuggestion{" +
                "blockId='" + blockId + '\'' +
                ", status=" + status +
                ", generatedAt=" + generatedAt +
                ", metadataSize=" + metadata.size() +
                '}';
    }
}
