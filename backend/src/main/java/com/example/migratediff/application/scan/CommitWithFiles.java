package com.example.migratediff.application.scan;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

/**
 * 带文件列表的提交
 */
@Data
@AllArgsConstructor
public class CommitWithFiles {
    
    /**
     * 提交信息
     */
    private final CommitInfo commit;
    
    /**
     * 该提交变更的文件路径集合
     */
    private final Set<String> changedFiles;
}
