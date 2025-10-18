package com.example.codecompare.rebuild.agent;

import java.util.Optional;

/**
 * Agent 建议缓存抽象，减少重复调用。
 */
public interface AgentSuggestionCache {

    /**
     * 根据代码块 ID 查询缓存。
     *
     * @param blockId 代码块编号
     * @return 已缓存的建议
     */
    Optional<AgentSuggestion> get(String blockId);

    /**
     * 写入或更新缓存。
     *
     * @param suggestion 建议内容
     */
    void put(AgentSuggestion suggestion);

    /**
     * 删除指定缓存。
     *
     * @param blockId 代码块编号
     */
    void evict(String blockId);
}
