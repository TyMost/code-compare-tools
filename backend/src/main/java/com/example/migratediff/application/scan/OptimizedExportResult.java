package com.example.migratediff.application.scan;

import com.example.migratediff.domain.repo.RepoType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 优化的导出结果 - 替换MultiRepoExportResult
 */
@Data
@Builder
public class OptimizedExportResult {
    
    /**
     * 生成时间
     */
    private final Instant generatedAt;
    
    /**
     * 分析的仓库名称列表
     */
    private final List<String> analyzedRepos;
    
    /**
     * 时间范围起始
     */
    private final Instant timeFrom;
    
    /**
     * 时间范围结束
     */
    private final Instant timeTo;
    
    /**
     * 仓库到文件提交映射的映射
     */
    private final Map<MultiRepoExportRequest.RepoSelection, FileCommitMapping> repoMappings;
    
    /**
     * 文件路径到提交记录的映射
     */
    private final Map<String, FileCommitRecords> fileCommitRecords;
    
    /**
     * 导出统计信息
     */
    private final ExportStatistics statistics;
    
    /**
     * 导出统计信息
     */
    @Data
    @Builder
    public static class ExportStatistics {
        
        /**
         * 总文件数
         */
        private final int totalFiles;
        
        /**
         * 有提交记录的文件数
         */
        private final int filesWithCommits;
        
        /**
         * 总提交数
         */
        private final int totalCommits;
        
        /**
         * 按仓库类型分组的提交数
         */
        private final Map<RepoType, Integer> commitsByRepoType;
        
        /**
         * 按仓库分组的统计信息
         */
        private final Map<String, RepoStatistics> repoStatistics;
        
        /**
         * 单个仓库的统计信息
         */
        @Data
        @Builder
        public static class RepoStatistics {
            
            /**
             * 仓库名称
             */
            private final String repoName;
            
            /**
             * 仓库类型
             */
            private final RepoType repoType;
            
            /**
             * 该仓库的文件数
             */
            private final int fileCount;
            
            /**
             * 该仓库的提交数
             */
            private final int commitCount;
            
            /**
             * 有提交的文件数
             */
            private final int filesWithCommits;
        }
    }
}
