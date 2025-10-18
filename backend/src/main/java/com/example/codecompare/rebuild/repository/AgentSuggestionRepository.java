package com.example.codecompare.rebuild.repository;

import com.example.codecompare.rebuild.repository.model.AgentSuggestionRecord;

import java.util.List;
import java.util.Optional;

/**
 * Agent 建议的持久化接口，负责保存与查询块级建议数据。
 */
public interface AgentSuggestionRepository {

    /**
     * 批量保存指定比对结果下的 Agent 建议记录。
     *
     * @param comparisonId 比对标识
     * @param filePath     文件相对路径
     * @param suggestions  建议集合
     */
    void saveAll(String comparisonId, String filePath, List<AgentSuggestionRecord> suggestions);

    /**
     * 查询指定比对与文件的最新 Agent 建议列表。
     *
     * @param comparisonId 比对标识
     * @param filePath     文件相对路径
     * @return 最新建议集合
     */
    List<AgentSuggestionRecord> findLatest(String comparisonId, String filePath);

    /**
     * 根据块决策 ID 查询对应的 Agent 建议。
     *
     * @param comparisonId 比对标识
     * @param decisionId   块决策 ID
     * @return 建议记录
     */
    Optional<AgentSuggestionRecord> findByDecision(String comparisonId, String decisionId);

    /**
     * 删除指定块决策关联的 Agent 建议。
     *
     * @param comparisonId 比对标识
     * @param decisionId   块决策 ID
     */
    void deleteByDecision(String comparisonId, String decisionId);

    /**
     * 删除指定文件的全部 Agent 建议。
     *
     * @param comparisonId 比对标识
     * @param filePath     文件相对路径
     */
    void deleteByFile(String comparisonId, String filePath);

    /**
     * 删除指定比对下的全部 Agent 建议记录。
     *
     * @param comparisonId 比对标识
     */
    void deleteAll(String comparisonId);
}
