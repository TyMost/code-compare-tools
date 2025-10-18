package com.example.codecompare.rebuild.repository;

import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 文件元数据的持久化仓储接口。
 */
public interface FileMetadataRepository {

    FileRecord save(FileRecord record);

    List<FileRecord> saveAll(Collection<FileRecord> records);

    Optional<FileRecord> findLatest(String projectCode, String path);

    List<FileRecord> findLatestByProject(String projectCode);

    PageResult<FileRecord> findHistory(String projectCode, PageRequest pageRequest);

    PageResult<FileRecord> findHistory(String projectCode, String path, PageRequest pageRequest);

    void deletePaths(String projectCode, Collection<String> paths);

    void deleteProject(String projectCode);

    void purgeOlderThan(String projectCode, Instant threshold);
}
