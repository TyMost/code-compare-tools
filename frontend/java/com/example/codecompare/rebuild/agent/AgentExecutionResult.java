package com.example.codecompare.rebuild.agent;

import java.time.Instant;

/**
 * Agent 执行结果摘要。
 */
public final class AgentExecutionResult {

    private final int processedBlocks;
    private final String message;
    private final Instant executedAt;

    public AgentExecutionResult(int processedBlocks, String message, Instant executedAt) {
        this.processedBlocks = processedBlocks;
        this.message = message;
        this.executedAt = executedAt == null ? Instant.now() : executedAt;
    }

    public int getProcessedBlocks() {
        return processedBlocks;
    }

    public String getMessage() {
        return message;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
