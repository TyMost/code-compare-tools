package com.example.migratediff.application.scan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 文件级别的详细提交信息
 * 用于Excel导出Sheet2中的文件详细提交列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileCommitDetail {
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 仓库来源：Oracle 或 Gauss
     */
    private String repoSource;
    
    /**
     * 提交哈希
     */
    private String commitHash;
    
    /**
     * 提交者姓名
     */
    private String authorName;
    
    /**
     * 提交者邮箱
     */
    private String authorEmail;
    
    /**
     * 提交时间
     */
    private Instant commitTime;
    
    /**
     * 提交消息
     */
    private String commitMessage;
    
    /**
     * 提交类型：feature/bugfix/refactor/hotfix等
     */
    private String commitType;
    
    /**
     * 文件变更类型：ADD/MODIFY/DELETE/RENAME等
     */
    private String changeType;
    
    /**
     * 添加的行数
     */
    private Integer linesAdded;
    
    /**
     * 删除的行数
     */
    private Integer linesRemoved;
    
    /**
     * 是否在指定时间范围内
     */
    private Boolean inTimeRange;
}
