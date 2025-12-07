package com.example.migratediff.application.scan;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 提交者统计信息
 * 用于报表第二页显示时间段内的提交者活动情况
 */
@Data
@Builder
public class CommitAuthorStats {
    
    /**
     * 仓库名称/别名
     */
    private String repoName;
    
    /**
     * 提交者姓名
     */
    private String authorName;
    
    /**
     * 提交者邮箱
     */
    private String authorEmail;
    
    /**
     * Oracle仓库提交次数
     */
    private Integer oracleCommitCount;
    
    /**
     * Gauss仓库提交次数
     */
    private Integer gaussCommitCount;
    
    /**
     * 总提交次数
     */
    private Integer totalCommitCount;
    
    /**
     * 首次提交时间
     */
    private Instant firstCommitTime;
    
    /**
     * 最近提交时间
     */
    private Instant lastCommitTime;
    
    /**
     * 主要提交文件数（去重）
     */
    private Integer affectedFilesCount;
    
    /**
     * 影响的文件路径列表
     */
    private Set<String> affectedFiles;
    
    /**
     * 常用提交消息类型（如：feature, bugfix, refactor等）
     */
    private List<String> commonCommitTypes;
    
    /**
     * 主要提交消息（最近几条）
     */
    private List<String> recentCommitMessages;
    
    /**
     * 提交者类型（oracle-only, gauss-only, both）
     */
    private String authorType;
    
    /**
     * 活跃度评分（基于提交频率和文件数量）
     */
    private Double activityScore;
}
