package com.example.migratediff.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Git提交信息DTO
 * 用于传输Git提交的元数据信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GitCommitInfoDTO {
    
    /**
     * 完整的提交哈希值
     */
    private String commitHash;
    
    /**
     * 短哈希值（前7位）
     */
    private String shortHash;
    
    /**
     * 作者姓名
     */
    private String authorName;
    
    /**
     * 作者邮箱
     */
    private String authorEmail;
    
    /**
     * 提交时间
     */
    private Instant commitTime;
    
    /**
     * 提交信息
     */
    private String message;
    
    /**
     * 分支名
     */
    private String branch;
    
    /**
     * Git Web URL（用于跳转到具体的提交页面）
     */
    private String url;
    
    /**
     * 提交统计信息
     */
    private CommitStatsDTO stats;
    
    /**
     * 仓库类型（oracle/gauss）
     */
    private String repoType;
    
    /**
     * 提交统计信息内部类
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CommitStatsDTO {
        /**
         * 新增行数
         */
        private Integer added;
        
        /**
         * 删除行数
         */
        private Integer removed;
        
        /**
         * 修改行数
         */
        private Integer modified;
        
        /**
         * 变更文件数
         */
        private Integer filesChanged;
    }
}
