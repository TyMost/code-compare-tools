package com.example.migratediff.application.scan;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 描述导出所需的状态和覆盖率过滤条件。
 * 简化版本：移除提交信息筛选的细分字段
 */
@Data
@Builder
public class DiffMatrixFilterCriteria {
    private List<String> statuses;
    private Double coverageMin;
    private Double coverageMax;
    @Builder.Default
    private boolean includeEmptyCoverage = true;
    private List<String> fileExtensions;
    @Builder.Default
    private boolean excludeTestFiles = false;
    private List<String> excludePatterns;
    
    /**
     * 是否包含提交信息 - 简化为总开关
     */
    @Builder.Default
    private boolean includeCommitInfo = false;
    
    /**
     * 作者类型筛选
     */
    private String authorTypeFilter;
}
