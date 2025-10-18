package com.example.codecompare.rebuild.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent 批量执行请求参数。
 */
public final class AgentExecutionRequest {

    private final String projectKey;
    private final List<String> blockIds;
    private final boolean forceRefreshSuggestion;

    public AgentExecutionRequest(String projectKey,
                                 List<String> blockIds,
                                 boolean forceRefreshSuggestion) {
        this.projectKey = projectKey;
        this.blockIds = blockIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(blockIds));
        this.forceRefreshSuggestion = forceRefreshSuggestion;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public List<String> getBlockIds() {
        return blockIds;
    }

    public boolean isForceRefreshSuggestion() {
        return forceRefreshSuggestion;
    }
}
