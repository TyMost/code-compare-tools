package com.example.migratediff.infrastructure.git.strategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.jgit.lib.ObjectId;

import java.time.Instant;

/**
 * 分支及其在时间窗口内的提交信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchWithCommits {
    /**
     * 分支名称
     */
    private String branchName;
    
    /**
     * 分支创建时间（分支的第一个提交时间）
     */
    private Instant branchCreationTime;
    
    /**
     * 时间窗口内的最晚提交
     */
    private ObjectId latestCommitInWindow;
    
    /**
     * 时间窗口内的最早提交
     */
    private ObjectId earliestCommitInWindow;
    
    /**
     * 时间窗口内的提交数量
     */
    private int commitCountInWindow;
    
    /**
     * 该分支是否在时间窗口内有提交
     */
    private boolean hasCommitsInWindow;
    
    /**
     * 搜索耗时（毫秒）
     */
    private long searchTimeMs;
    
    /**
     * 创建一个空的结果
     */
    public static BranchWithCommits empty(String branchName) {
        BranchWithCommits result = new BranchWithCommits();
        result.setBranchName(branchName);
        result.setHasCommitsInWindow(false);
        result.setCommitCountInWindow(0);
        return result;
    }
    
    /**
     * 创建一个有效的结果
     */
    public static BranchWithCommits of(String branchName, Instant branchCreationTime, 
                                     ObjectId latestCommitInWindow, ObjectId earliestCommitInWindow,
                                     int commitCountInWindow, long searchTimeMs) {
        BranchWithCommits result = new BranchWithCommits();
        result.setBranchName(branchName);
        result.setBranchCreationTime(branchCreationTime);
        result.setLatestCommitInWindow(latestCommitInWindow);
        result.setEarliestCommitInWindow(earliestCommitInWindow);
        result.setCommitCountInWindow(commitCountInWindow);
        result.setHasCommitsInWindow(latestCommitInWindow != null);
        result.setSearchTimeMs(searchTimeMs);
        return result;
    }
    
    /**
     * 检查结果是否有效
     */
    public boolean isValid() {
        return branchName != null 
                && branchCreationTime != null 
                && hasCommitsInWindow 
                && latestCommitInWindow != null;
    }
    
    /**
     * 获取时间窗口内提交的哈希字符串（用于日志）
     */
    public String getLatestCommitHash() {
        return latestCommitInWindow != null ? latestCommitInWindow.name() : "null";
    }
    
    /**
     * 获取时间窗口内最早提交的哈希字符串（用于日志）
     */
    public String getEarliestCommitHash() {
        return earliestCommitInWindow != null ? earliestCommitInWindow.name() : "null";
    }
}
