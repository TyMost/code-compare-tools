package com.example.migratediff.application.scan;

import com.example.migratediff.application.scan.MultiRepoExportRequest.RepoSelection;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件提交记录 - 替换FileCommitDetail
 */
@Data
@Builder
public class FileCommitRecords {
    
    /**
     * 文件路径
     */
    private final String filePath;
    
    /**
     * Oracle仓库的提交记录
     * Key: 仓库选择
     * Value: 该仓库中该文件的所有Oracle提交
     */
    @Builder.Default
    private final Map<RepoSelection, List<CommitInfo>> oracleRecords = new HashMap<>();
    
    /**
     * Gauss仓库的提交记录
     * Key: 仓库选择
     * Value: 该仓库中该文件的所有Gauss提交
     */
    @Builder.Default
    private final Map<RepoSelection, List<CommitInfo>> gaussRecords = new HashMap<>();
    
    /**
     * 添加Oracle提交记录
     */
    public void addOracleRecords(RepoSelection selection, List<CommitInfo> commits) {
        if (commits != null && !commits.isEmpty()) {
            oracleRecords.put(selection, commits);
        }
    }
    
    /**
     * 添加Gauss提交记录
     */
    public void addGaussRecords(RepoSelection selection, List<CommitInfo> commits) {
        if (commits != null && !commits.isEmpty()) {
            gaussRecords.put(selection, commits);
        }
    }
    
    /**
     * 是否有任何提交记录
     */
    public boolean hasAnyCommits() {
        return !oracleRecords.isEmpty() || !gaussRecords.isEmpty();
    }
    
    /**
     * 获取所有Oracle提交数
     */
    public int getTotalOracleCommits() {
        return oracleRecords.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    /**
     * 获取所有Gauss提交数
     */
    public int getTotalGaussCommits() {
        return gaussRecords.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    /**
     * 获取总提交数
     */
    public int getTotalCommits() {
        return getTotalOracleCommits() + getTotalGaussCommits();
    }
}
