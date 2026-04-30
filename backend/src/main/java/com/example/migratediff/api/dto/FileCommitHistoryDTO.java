package com.example.migratediff.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件提交历史DTO
 * 用于传输单个文件的Oracle和Gauss提交历史信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileCommitHistoryDTO {
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * Oracle仓库的提交历史
     */
    private List<GitCommitInfoDTO> oracleCommits;
    
    /**
     * Gauss仓库的提交历史
     */
    private List<GitCommitInfoDTO> gaussCommits;
    
    /**
     * 总提交数量
     */
    private Integer totalCount;
    
    /**
     * Oracle提交数量
     */
    private Integer oracleCount;
    
    /**
     * Gauss提交数量
     */
    private Integer gaussCount;
    
    /**
     * 是否有Oracle提交历史
     */
    private Boolean hasOracleCommits;
    
    /**
     * 是否有Gauss提交历史
     */
    private Boolean hasGaussCommits;
}
