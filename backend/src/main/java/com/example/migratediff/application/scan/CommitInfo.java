package com.example.migratediff.application.scan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import com.example.migratediff.domain.repo.RepoType;

/**
 * 简化的提交信息
 */
@Data
@Builder
@AllArgsConstructor
public class CommitInfo {
    
    /**
     * 完整提交哈希
     */
    private final String commitHash;
    
    /**
     * 短哈希（前7位）
     */
    private final String shortHash;
    
    /**
     * 提交者姓名
     */
    private final String authorName;
    
    /**
     * 提交者邮箱
     */
    private final String authorEmail;
    
    /**
     * 提交时间
     */
    private final java.time.Instant commitTime;
    
    /**
     * 提交消息
     */
    private final String message;
    
    /**
     * 仓库类型
     */
    private final RepoType repoType;
    
    /**
     * 提交类型（自动识别）
     */
    private final String commitType;
    
    /**
     * 变更的文件数
     */
    private final int changedFiles;
    
    /**
     * 添加的行数
     */
    private final int linesAdded;
    
    /**
     * 删除的行数
     */
    private final int linesRemoved;
}
