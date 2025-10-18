package com.example.codecompare.rebuild.api.dto;

/**
 * Agent 建议占位视图。
 */
public class AgentSuggestionView {

    private final String blockId;
    private final boolean enabled;
    private final String aiModel;
    private final String suggestedCode;
    private final String reason;
    private final Double confidence;
    private final String message;

    public AgentSuggestionView(String blockId,
                               boolean enabled,
                               String aiModel,
                               String suggestedCode,
                               String reason,
                               Double confidence,
                               String message) {
        this.blockId = blockId;
        this.enabled = enabled;
        this.aiModel = aiModel;
        this.suggestedCode = suggestedCode;
        this.reason = reason;
        this.confidence = confidence;
        this.message = message;
    }

    public String getBlockId() {
        return blockId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getSuggestedCode() {
        return suggestedCode;
    }

    public String getReason() {
        return reason;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getMessage() {
        return message;
    }
}
