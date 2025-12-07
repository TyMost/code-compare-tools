package com.example.migratediff.application.scan;

import lombok.Builder;
import lombok.Data;

/**
 * 扫描矩阵中单个文件的统计信息，用于前后端共用的报表导出逻辑。
 */
@Data
@Builder
public class DiffMatrixRow {

    private String filePath;
    private int oracleAdded;
    private int oracleRemoved;
    private int gaussAdded;
    private int gaussRemoved;
    /**
     * 覆盖率（0~1），允许为空表示该文件尚未计算覆盖情况。
     */
    private Double coverage;
    /**
     * 状态标签：matched/oracle-only/gauss-only/partial/pending/processing。
     */
    private String status;

    // ========== 提交信息相关字段 ==========
    
    /**
     * Oracle仓库最后提交者姓名
     */
    private String lastOracleAuthor;
    
    /**
     * Oracle仓库最后提交时间
     */
    private java.time.Instant lastOracleCommitTime;
    
    /**
     * Oracle仓库最后提交消息
     */
    private String lastOracleCommitMessage;
    
    /**
     * Oracle仓库最后提交哈希
     */
    private String lastOracleCommitHash;
    
    /**
     * Gauss仓库最后提交者姓名
     */
    private String lastGaussAuthor;
    
    /**
     * Gauss仓库最后提交时间
     */
    private java.time.Instant lastGaussCommitTime;
    
    /**
     * Gauss仓库最后提交消息
     */
    private String lastGaussCommitMessage;
    
    /**
     * Gauss仓库最后提交哈希
     */
    private String lastGaussCommitHash;
    
    /**
     * Oracle仓库提交总数
     */
    private Integer oracleCommitCount;
    
    /**
     * Gauss仓库提交总数
     */
    private Integer gaussCommitCount;
    
    /**
     * 是否有Oracle提交历史
     */
    private Boolean hasOracleCommits;
    
    /**
     * 是否有Gauss提交历史
     */
    private Boolean hasGaussCommits;
}
