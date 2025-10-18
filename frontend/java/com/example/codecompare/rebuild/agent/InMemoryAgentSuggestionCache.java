package com.example.codecompare.rebuild.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 简单的本地内存缓存实现，满足离线场景需求。
 */
@Component
public class InMemoryAgentSuggestionCache implements AgentSuggestionCache {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentSuggestionCache.class);

    private final ConcurrentMap<String, AgentSuggestion> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentSuggestion> get(String blockId) {
        if (blockId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(blockId));
    }

    @Override
    public void put(AgentSuggestion suggestion) {
        if (suggestion == null || suggestion.getBlockId() == null) {
            return;
        }
        cache.put(suggestion.getBlockId(), suggestion);
        log.info("Agent 建议已写入缓存，blockId={}", suggestion.getBlockId());
    }

    @Override
    public void evict(String blockId) {
        if (blockId == null) {
            return;
        }
        cache.remove(blockId);
        log.info("Agent 建议缓存已清理，blockId={}", blockId);
    }
}
