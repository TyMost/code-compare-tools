package com.example.codecompare.rebuild.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 默认占位实现，保障在未接入真实 AI 时有可用响应。
 */
@Component
public class PlaceholderMigrationAgent implements MigrationAgent {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderMigrationAgent.class);
    private static final String PLACEHOLDER_MESSAGE = "等待后续建设";

    @Override
    public AgentSuggestion generateSuggestion(AgentTaskContext context) {
        String blockId = context == null ? "" : context.getBlockId();
        log.info("使用占位 Agent 生成建议，blockId={}", blockId);
        return new AgentSuggestion(blockId, PLACEHOLDER_MESSAGE,
                AgentSuggestionStatus.PLACEHOLDER, Instant.now());
    }
}
