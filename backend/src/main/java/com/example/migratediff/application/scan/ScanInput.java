package com.example.migratediff.application.scan;

import com.example.migratediff.domain.diff.DiffSummary;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScanInput {
    String taskId;
    boolean persistResult;
    /**
     * 触发扫描的预设名称（若未使用预设则为空），用于导出报表时展示仓库来源。
     */
    String presetName;
    /**
     * 可选仓库配置标识，用于快照缓存。
     */
    String repoId;
    /**
     * 可选仓库展示名称，用于快照缓存。
     */
    String repoName;
    DiffSummary oracleSummary;
    DiffSummary gaussSummary;
    ScanMode mode;
}
