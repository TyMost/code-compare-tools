package com.example.codecompare.rebuild.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 预留的远程建议网关，当前不进行真实调用。
 */
@Component
public class AgentSuggestionGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentSuggestionGateway.class);

    /**
     * 尝试从远程服务获取建议；当前直接返回空占位，等待后续扩展。
     */
    public Optional<AgentSuggestion> fetchRemoteSuggestion(AgentTaskContext context) {
        log.info("远程 Agent 接入未完成，返回占位建议。");
        return Optional.empty();
    }
}
