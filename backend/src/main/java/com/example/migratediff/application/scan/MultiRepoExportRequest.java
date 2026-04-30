package com.example.migratediff.application.scan;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 多仓导出领域请求对象。
 */
@Value
@Builder
public class MultiRepoExportRequest {
    List<RepoSelection> repos;
    DiffMatrixFilterCriteria filterCriteria;
    String format;

    @Value
    @Builder
    public static class RepoSelection {
        String taskId;
        String presetName;
        String alias;
    }
}
