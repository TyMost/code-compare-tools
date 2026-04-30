package com.example.migratediff.domain.coverage.optimization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单向覆盖匹配结果
 * 表示origin内容在target文件中的匹配情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {
    
    /**
     * 匹配到的内容
     */
    private String content;
    
    /**
     * 起始行号（1基）
     */
    private int startLine;
    
    /**
     * 结束行号（1基）
     */
    private int endLine;
    
    /**
     * 匹配置信度（0-1）
     */
    private double confidence;
    
    /**
     * 匹配类型
     */
    private MatchType matchType;
    
    /**
     * 是否更新了原始内容
     */
    private boolean contentUpdated;
    
    /**
     * 匹配位置偏移（字符位置）
     */
    private int charPosition;
    
    /**
     * 上下文扩展的行数
     */
    private int expandedLines;
    
    /**
     * 匹配耗时（毫秒）
     */
    private long matchTimeMs;
    
    /**
     * 是否为精确匹配
     */
    public boolean isExactMatch() {
        return matchType == MatchType.EXACT;
    }
    
    /**
     * 是否有有效匹配
     */
    public boolean hasMatch() {
        return matchType != MatchType.NONE && content != null && !content.trim().isEmpty();
    }
    
    /**
     * 获取匹配内容长度
     */
    public int getContentLength() {
        return content != null ? content.length() : 0;
    }
    
    /**
     * 获取匹配行数
     */
    public int getLineCount() {
        return endLine - startLine + 1;
    }
}
