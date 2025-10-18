package com.example.codecompare.rebuild.repository;

import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * 块级决策快照的持久化接口，提供按文件与比对维度的读写能力。
 */
public interface BlockDecisionRepository {

    BlockDecisionSnapshot save(BlockDecisionSnapshot snapshot);

    Optional<BlockDecisionSnapshot> findLatest(String comparisonId, String filePath);

    PageResult<BlockDecisionSnapshot> findHistory(String comparisonId, PageRequest pageRequest);

    PageResult<BlockDecisionSnapshot> findHistory(String comparisonId, String filePath, PageRequest pageRequest);

    void delete(String comparisonId, String filePath);

    void deleteAll(String comparisonId);

    void purgeOlderThan(String comparisonId, Instant threshold);

    /**
     * 列出当前已持久化的 comparisonId 列表，便于跨项目检索代码块。
     */
    Set<String> listComparisonIds();
}
