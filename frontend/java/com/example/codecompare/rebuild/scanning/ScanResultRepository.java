package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.repository.model.FileRecord;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 扫描结果仓储，封装文件指纹与摘要的持久化操作。
 */
public interface ScanResultRepository {

    List<FileRecord> saveAll(Collection<FileRecord> records);

    Optional<ScanSummary> findLatestSummary(String projectCode);

    void saveSummary(ScanSummary summary);

    List<FileRecord> findLatestFiles(String projectCode);

    void deletePaths(String projectCode, Collection<String> paths);

    void deleteSummary(String projectCode);
}
