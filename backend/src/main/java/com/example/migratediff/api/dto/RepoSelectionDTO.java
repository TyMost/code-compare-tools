package com.example.migratediff.api.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 单个仓库/任务的选择信息，允许通过 taskId 或 presetName 锁定。
 */
@Data
public class RepoSelectionDTO {

    @Size(max = 128, message = "taskId 过长")
    private String taskId;

    @Size(max = 128, message = "presetName 过长")
    private String presetName;

    /**
     * 导出时展示的别名，方便辨识。
     */
    @Size(max = 128, message = "别名过长")
    private String alias;
}
