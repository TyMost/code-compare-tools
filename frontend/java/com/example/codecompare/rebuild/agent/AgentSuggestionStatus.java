package com.example.codecompare.rebuild.agent;

/**
 * Agent 建议状态，区分占位与真实结果。
 */
public enum AgentSuggestionStatus {

    /**
     * 尚未接入真实 Agent，返回占位提示。
     */
    PLACEHOLDER("占位"),

    /**
     * 本地规则命中并生成建议。
     */
    GENERATED("已生成"),

    /**
     * 生成失败或规则缺失。
     */
    FAILED("失败");

    private final String label;

    AgentSuggestionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
