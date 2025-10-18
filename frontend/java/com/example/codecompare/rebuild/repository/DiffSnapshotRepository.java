package com.example.codecompare.rebuild.repository;

import com.example.codecompare.rebuild.repository.model.DiffSnapshotDocument;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;

import java.time.Instant;
import java.util.Optional;

/**
 * 差异快照的持久化接口，用于记录文件级 diff 指标与摘要信息。
 */
public interface DiffSnapshotRepository {

    DiffSnapshotDocument save(DiffSnapshotDocument snapshot);

    Optional<DiffSnapshotDocument> findLatest(String comparisonId, String filePath);

    PageResult<DiffSnapshotDocument> findRecent(String comparisonId, PageRequest pageRequest);

    void delete(String comparisonId, String filePath);

    void deleteAll(String comparisonId);

    void purgeOlderThan(String comparisonId, Instant threshold);
}
