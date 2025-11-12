package com.example.migratediff.domain.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationResult {

    private MigrationTask task;
    private boolean success;
    private String message;
    @Builder.Default
    private List<String> affectedFiles = new ArrayList<>();
    /**
     * 差异块级别的迁移建议集合。
     */
    @Builder.Default
    private List<MigrationBlockResult> blockResults = new ArrayList<>();
    /**
     * 聚合后的模板文本，便于前端直接预览。
     */
    @Builder.Default
    private String previewContent = "";

    /**
     * 操作日志标识，便于前端跳转或查询。
     */
    private Long logId;
}
