package com.example.migratediff.api.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
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
    /**
     * 目前仅支持 csv，可为后续扩展预留。
     */
    private String format = "csv";
}
