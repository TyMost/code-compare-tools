package com.example.migratediff.domain.coverage.optimization.v2;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 候选代码段
 * 表示在target文件中发现的与origin内容可能匹配的代码段
 */
@Data
@Builder
public class CandidateSegment {
    
    /**
     * 起始行号（1基）
     */
    private int startLine;
    
    /**
     * 结束行号（1基）
     */
    private int endLine;
    
    /**
     * 代码段内容
     */
    private String content;
    
    /**
     * 发现阶段综合评分（0-1）
     */
    private double discoverScore;
    
    /**
     * token命中数量
     */
    private int tokenHitCount;
    
    /**
     * n-gram命中比例
     */
    private double ngramHitRatio;
    
    /**
     * 结构相似度（基于if/loop/return/call数量）
     */
    private double structureSimilarity;
    
    /**
     * 验证阶段覆盖率（如果已验证）
     */
    private Double verifyCoverage;
    
    /**
     * 行级覆盖映射（行内容 → 是否被覆盖）
     */
    private Map<String, Integer> lineCoverageMap;
    
    /**
     * 综合排名
     */
    private int rank;
    
    /**
     * 上下文扩展的行数
     */
    private int expandedLines;
    
    /**
     * 获取代码段行数
     * 
     * @return 行数
     */
    public int getLineCount() {
        return endLine - startLine + 1;
    }
    
    /**
     * 检查是否已被验证
     * 
     * @return 是否已验证
     */
    public boolean isVerified() {
        return verifyCoverage != null;
    }
    
    /**
     * 获取平均每行字符数
     * 
     * @return 平均字符数
     */
    public double getAvgCharsPerLine() {
        int lineCount = getLineCount();
        return content != null && lineCount > 0 ? (double) content.length() / lineCount : 0.0;
    }
    
    /**
     * 获取代码段的长度（字符数）
     * 
     * @return 字符数
     */
    public int getContentLength() {
        return content != null ? content.length() : 0;
    }
    
    /**
     * 检查是否为有效候选
     * 
     * @return 是否有效（非空内容且行数>0）
     */
    public boolean isValid() {
        return content != null && !content.trim().isEmpty() && getLineCount() > 0;
    }
    
    /**
     * 获取代码段的简短描述
     * 
     * @return 描述字符串
     */
    public String getDescription() {
        return String.format("Lines %d-%d (%d lines, score: %.2f, coverage: %.2f)", 
            startLine, endLine, getLineCount(), discoverScore, 
            verifyCoverage != null ? verifyCoverage : 0.0);
    }
}
