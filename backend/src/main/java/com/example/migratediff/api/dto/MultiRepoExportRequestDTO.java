package com.example.migratediff.api.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 多仓导出请求，包含需要聚合的任务以及统一的过滤条件。
 */
@Data
public class MultiRepoExportRequestDTO {

    @Valid
    @NotEmpty(message = "至少需要选择一个仓库任务")
    private List<RepoSelectionDTO> repos = new ArrayList<>();

    private List<String> statuses = new ArrayList<>();
    private Double coverageMin;
    private Double coverageMax;
    private boolean includeEmptyCoverage = true;
    private List<String> fileExtensions = new ArrayList<>();
    private boolean excludeTestFiles = false;
    private List<String> excludePatterns = new ArrayList<>();
    
    // ========== 提交信息相关字段 ==========
    
    /**
     * 是否包含提交信息
     */
    private boolean includeCommitInfo = false;
    
    /**
     * 提交者筛选
     */
    private List<String> authorFilters = new ArrayList<>();
    
    /**
     * 提交者类型筛选（oracle-only, gauss-only, both）
     */
    private String authorTypeFilter;
    
    /**
     * 时间范围开始（用于提交者统计）
     */
    private Instant timeFrom;
    
    /**
     * 时间范围结束（用于提交者统计）
     */
    private Instant timeTo;
    
    /**
     * 目前仅支持 csv，可为后续扩展预留。
     */
    private String format = "csv";
}
