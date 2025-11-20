package com.example.migratediff.application.scan;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 描述导出所需的状态和覆盖率过滤条件。
 */
@Data
@Builder
public class DiffMatrixFilterCriteria {
    private List<String> statuses;
    private Double coverageMin;
    private Double coverageMax;
    @Builder.Default
    private boolean includeEmptyCoverage = true;
}
