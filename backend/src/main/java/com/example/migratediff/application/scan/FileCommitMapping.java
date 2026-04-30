package com.example.migratediff.application.scan;

import com.example.migratediff.domain.repo.RepoType;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.application.scan.MultiRepoExportRequest.RepoSelection;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 文件提交映射 - 替换原有复杂结构
 */
@Data
@Builder
public class FileCommitMapping {
    
    /**
     * 仓库选择
     */
    private final RepoSelection repoSelection;
    
    /**
     * 时间范围起始
     */
    private final Instant timeFrom;
    
    /**
     * 时间范围结束
     */
    private final Instant timeTo;
    
    /**
     * Oracle仓库映射
     */
    private final RepoMapping oracleMapping;
    
    /**
     * Gauss仓库映射
     */
    private final RepoMapping gaussMapping;
    
    /**
     * 构建时间
     */
    private final Instant buildAt;
    
    /**
     * 单个仓库的映射信息
     */
    @Data
    @Builder
    public static class RepoMapping {
        
        /**
         * 仓库配置
         */
        private final RepoConfig repoConfig;
        
        /**
         * 仓库类型
         */
        private final RepoType repoType;
        
        /**
         * 时间范围起始
         */
        private final Instant timeFrom;
        
        /**
         * 时间范围结束
         */
        private final Instant timeTo;
        
        /**
         * 文件到提交列表的映射
         * Key: 文件路径
         * Value: 该文件的所有提交（按时间倒序）
         */
        private final Map<String, List<CommitInfo>> fileToCommits;
        
        /**
         * 总提交数
         */
        private final int totalCommits;
        
        /**
         * 总文件数
         */
        private final int totalFiles;
        
        /**
         * 构建时间
         */
        private final Instant buildAt;
    }
}
