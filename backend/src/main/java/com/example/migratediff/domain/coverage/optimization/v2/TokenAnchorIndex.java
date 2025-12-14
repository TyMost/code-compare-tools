package com.example.migratediff.domain.coverage.optimization.v2;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Token锚点索引
 * 构建：token → [行号列表]
 * 用于快速定位包含特定token的代码行
 */
@Slf4j
public class TokenAnchorIndex {
    
    private final Map<String, List<Integer>> tokenToLines = new HashMap<>();
    private final List<String> lines;
    private final int minTokenLength;
    
    /**
     * 构造函数
     * 
     * @param fileContent 文件内容
     * @param minTokenLength 最小token长度
     */
    public TokenAnchorIndex(String fileContent, int minTokenLength) {
        this.lines = Arrays.asList(fileContent.split("\n"));
        this.minTokenLength = minTokenLength;
        buildIndex();
        log.debug("Token锚点索引构建完成: {} tokens, {} lines", 
                   tokenToLines.size(), lines.size());
    }
    
    /**
     * 构建token到行号的索引
     */
    private void buildIndex() {
        for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
            String line = lines.get(lineNum);
            Set<String> tokens = extractTokens(line);
            
            for (String token : tokens) {
                tokenToLines.computeIfAbsent(token, k -> new ArrayList<>())
                           .add(lineNum + 1); // 转换为1基行号
            }
        }
        
        // 对每个token的行号列表进行排序和去重
        tokenToLines.values().forEach(lineList -> {
            Collections.sort(lineList);
            // 去重（保持有序）
            List<Integer> uniqueLines = lineList.stream()
                .distinct()
                .collect(Collectors.toList());
            lineList.clear();
            lineList.addAll(uniqueLines);
        });
    }
    
    /**
     * 从单行提取tokens
     * 
     * @param line 代码行
     * @return token集合
     */
    private Set<String> extractTokens(String line) {
        Set<String> tokens = new HashSet<>();
        
        // 提取长度≥minTokenLength的token
        // 匹配字母数字下划线组成的标识符
        String[] words = line.replaceAll("[^a-zA-Z0-9_]", " ").split("\\s+");
        
        for (String word : words) {
            if (word.length() >= minTokenLength) {
                tokens.add(word.toLowerCase());
            }
        }
        
        return tokens;
    }
    
    /**
     * 获取token到行号的映射
     * 
     * @return 不可修改的映射
     */
    public Map<String, List<Integer>> getTokenToLines() {
        return Collections.unmodifiableMap(tokenToLines);
    }
    
    /**
     * 获取所有行内容
     * 
     * @return 行列表
     */
    public List<String> getLines() {
        return lines;
    }
    
    /**
     * 查找包含指定tokens的行号
     * 
     * @param tokens 要查找的token集合
     * @return 命中的行号列表（不重复）
     */
    public List<Integer> findLinesWithTokens(Set<String> tokens) {
        Set<Integer> result = new HashSet<>();
        
        for (String token : tokens) {
            List<Integer> lines = tokenToLines.get(token);
            if (lines != null) {
                result.addAll(lines);
            }
        }
        
        return result.stream()
                   .sorted()
                   .collect(Collectors.toList());
    }
    
    /**
     * 查找包含指定tokens的行号，支持最少命中数
     * 
     * @param tokens 要查找的token集合
     * @param minHitCount 最少命中数
     * @return 满足条件的行号列表
     */
    public List<Integer> findLinesWithMinHits(Set<String> tokens, int minHitCount) {
        // 统计每行的token命中数
        Map<Integer, Integer> lineHitCounts = new HashMap<>();
        
        for (String token : tokens) {
            List<Integer> lines = tokenToLines.get(token);
            if (lines != null) {
                for (Integer lineNum : lines) {
                    lineHitCounts.merge(lineNum, 1, Integer::sum);
                }
            }
        }
        
        // 筛选命中数足够的行
        return lineHitCounts.entrySet().stream()
                       .filter(entry -> entry.getValue() >= minHitCount)
                       .map(Map.Entry::getKey)
                       .sorted()
                       .collect(Collectors.toList());
    }
    
    /**
     * 获取指定行的内容
     * 
     * @param lineNum 行号（1基）
     * @return 行内容，如果行号无效返回null
     */
    public String getLine(int lineNum) {
        if (lineNum < 1 || lineNum > lines.size()) {
            return null;
        }
        return lines.get(lineNum - 1);
    }
    
    /**
     * 获取指定行范围的内容
     * 
     * @param startLine 起始行号（1基）
     * @param endLine 结束行号（1基）
     * @return 行范围内容
     */
    public String getLines(int startLine, int endLine) {
        if (startLine < 1 || endLine > lines.size() || startLine > endLine) {
            return "";
        }
        
        return lines.stream()
                 .skip(startLine - 1)
                 .limit(endLine - startLine + 1)
                 .collect(Collectors.joining("\n"));
    }
    
    /**
     * 获取文件总行数
     * 
     * @return 总行数
     */
    public int getTotalLines() {
        return lines.size();
    }
    
    /**
     * 获取索引统计信息
     * 
     * @return 统计信息
     */
    public IndexStats getStats() {
        return IndexStats.builder()
                .totalTokens(tokenToLines.size())
                .totalLines(lines.size())
                .avgTokensPerLine(lines.isEmpty() ? 0 : (double) tokenToLines.size() / lines.size())
                .minTokenLength(minTokenLength)
                .build();
    }
    
    /**
     * 索引统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class IndexStats {
        private int totalTokens;
        private int totalLines;
        private double avgTokensPerLine;
        private int minTokenLength;
    }
}
