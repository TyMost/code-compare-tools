package com.example.migratediff.domain.coverage.optimization.v2;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 发现阶段
 * 在target完整文件中发现origin内容的候选代码段
 */
@Slf4j
public class DiscoverPhase {
    
    private final TokenAnchorIndex targetIndex;
    private final String originContent;
    private final DiscoverConfig config;
    
    /**
     * 构造函数
     * 
     * @param targetContent target文件内容
     * @param originContent origin内容
     * @param config 发现阶段配置
     */
    public DiscoverPhase(String targetContent, String originContent, DiscoverConfig config) {
        this.targetIndex = new TokenAnchorIndex(targetContent, config.getMinTokenLength());
        this.originContent = originContent;
        this.config = config;
    }
    
    /**
     * 执行发现阶段
     * 
     * @return 候选代码段列表
     */
    public List<CandidateSegment> discoverCandidates() {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("开始发现阶段: origin内容长度={}, target文件行数={}", 
                     originContent.length(), targetIndex.getTotalLines());
            
            // 1. 提取origin tokens
            Set<String> originTokens = extractOriginTokens();
            if (originTokens.isEmpty()) {
                log.debug("未提取到有效的origin tokens");
                return Collections.emptyList();
            }
            
            // 2. 基于token锚点发现候选行
            List<Integer> candidateLines = findCandidateLines(originTokens);
            if (candidateLines.isEmpty()) {
                log.debug("未找到包含origin tokens的候选行");
                return Collections.emptyList();
            }
            
            // 3. 以候选行为中心扩展形成候选段
            List<CandidateSegment> rawCandidates = expandToSegments(candidateLines);
            
            // 4. 结构轮廓过滤
            List<CandidateSegment> filteredCandidates = filterByStructure(rawCandidates);
            
            // 5. n-gram评分（大文件跳过）
            if (targetIndex.getTotalLines() <= config.getMaxLinesForNgram()) {
                scoreByNgram(filteredCandidates);
            }
            
            // 6. 综合评分排序，保留Top N
            List<CandidateSegment> topCandidates = rankAndSelect(filteredCandidates);
            
            long discoverTime = System.currentTimeMillis() - startTime;
            log.debug("发现阶段完成: 总候选={}, 过滤后={}, 保留Top={}, 耗时={}ms", 
                     rawCandidates.size(), filteredCandidates.size(), topCandidates.size(), discoverTime);
            
            return topCandidates;
            
        } catch (Exception e) {
            log.warn("发现阶段执行失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 提取origin内容的tokens
     * 
     * @return token集合
     */
    private Set<String> extractOriginTokens() {
        Set<String> tokens = new HashSet<>();
        
        // 使用与TokenAnchorIndex相同的提取逻辑
        String[] lines = originContent.split("\n");
        for (String line : lines) {
            String[] words = line.replaceAll("[^a-zA-Z0-9_]", " ").split("\\s+");
            for (String word : words) {
                if (word.length() >= config.getMinTokenLength()) {
                    tokens.add(word.toLowerCase());
                }
            }
        }
        
        log.debug("提取到{}个origin tokens: {}", tokens.size(), tokens);
        return tokens;
    }
    
    /**
     * 基于token锚点发现候选行
     * 
     * @param originTokens origin tokens
     * @return 候选行号列表
     */
    private List<Integer> findCandidateLines(Set<String> originTokens) {
        // 使用最少命中数策略
        return targetIndex.findLinesWithMinHits(originTokens, config.getMinTokenHit());
    }
    
    /**
     * 以候选行为中心扩展形成候选段
     * 
     * @param candidateLines 候选行号列表
     * @return 原始候选段列表
     */
    private List<CandidateSegment> expandToSegments(List<Integer> candidateLines) {
        List<CandidateSegment> segments = new ArrayList<>();
        
        for (Integer centerLine : candidateLines) {
            // 扩展半径
            int startLine = Math.max(1, centerLine - config.getExpandRadius());
            int endLine = Math.min(targetIndex.getTotalLines(), centerLine + config.getExpandRadius());
            
            String content = targetIndex.getLines(startLine, endLine);
            if (content != null && !content.trim().isEmpty()) {
                segments.add(CandidateSegment.builder()
                        .startLine(startLine)
                        .endLine(endLine)
                        .content(content)
                        .expandedLines(centerLine - startLine)
                        .build());
            }
        }
        
        return segments;
    }
    
    /**
     * 结构轮廓过滤
     * 比较origin与candidate的：行数、if/loop/return/call数量
     * 误差≤40%即保留
     * 
     * @param candidates 原始候选列表
     * @return 过滤后的候选列表
     */
    private List<CandidateSegment> filterByStructure(List<CandidateSegment> candidates) {
        // 计算origin结构特征
        StructureFeatures originFeatures = extractStructureFeatures(originContent);
        
        List<CandidateSegment> filtered = new ArrayList<>();
        
        for (CandidateSegment candidate : candidates) {
            StructureFeatures candidateFeatures = extractStructureFeatures(candidate.getContent());
            
            // 比较各项特征，误差≤容忍度即保留
            if (isStructureSimilar(originFeatures, candidateFeatures, config.getStructureTolerance())) {
                double similarity = calculateStructureSimilarity(originFeatures, candidateFeatures);
                candidate.setStructureSimilarity(similarity);
                filtered.add(candidate);
            }
        }
        
        log.debug("结构过滤: 候选{}个, 过滤后{}个", candidates.size(), filtered.size());
        return filtered;
    }
    
    /**
     * 检查结构是否相似
     * 
     * @param f1 结构特征1
     * @param f2 结构特征2
     * @param tolerance 误差容忍度
     * @return 是否相似
     */
    private boolean isStructureSimilar(StructureFeatures f1, StructureFeatures f2, double tolerance) {
        return Math.abs(f1.lineCount - f2.lineCount) <= f1.lineCount * tolerance &&
               Math.abs(f1.ifCount - f2.ifCount) <= Math.max(f1.ifCount, 1) * tolerance &&
               Math.abs(f1.loopCount - f2.loopCount) <= Math.max(f1.loopCount, 1) * tolerance &&
               Math.abs(f1.returnCount - f2.returnCount) <= Math.max(f1.returnCount, 1) * tolerance &&
               Math.abs(f1.callCount - f2.callCount) <= Math.max(f1.callCount, 1) * tolerance;
    }
    
    /**
     * 计算结构相似度
     * 
     * @param f1 结构特征1
     * @param f2 结构特征2
     * @return 相似度（0-1）
     */
    private double calculateStructureSimilarity(StructureFeatures f1, StructureFeatures f2) {
        double lineSimilarity = 1.0 - (double) Math.abs(f1.lineCount - f2.lineCount) / Math.max(f1.lineCount, f2.lineCount);
        double ifSimilarity = f1.ifCount == 0 && f2.ifCount == 0 ? 1.0 : 
                          1.0 - (double) Math.abs(f1.ifCount - f2.ifCount) / Math.max(f1.ifCount, f2.ifCount);
        double loopSimilarity = f1.loopCount == 0 && f2.loopCount == 0 ? 1.0 :
                             1.0 - (double) Math.abs(f1.loopCount - f2.loopCount) / Math.max(f1.loopCount, f2.loopCount);
        double returnSimilarity = f1.returnCount == 0 && f2.returnCount == 0 ? 1.0 :
                                1.0 - (double) Math.abs(f1.returnCount - f2.returnCount) / Math.max(f1.returnCount, f2.returnCount);
        double callSimilarity = f1.callCount == 0 && f2.callCount == 0 ? 1.0 :
                             1.0 - (double) Math.abs(f1.callCount - f2.callCount) / Math.max(f1.callCount, f2.callCount);
        
        return (lineSimilarity + ifSimilarity + loopSimilarity + returnSimilarity + callSimilarity) / 5.0;
    }
    
    /**
     * n-gram评分
     * n = 3 或 4，只统计命中比例，不做相似度
     * 
     * @param candidates 候选列表
     */
    private void scoreByNgram(List<CandidateSegment> candidates) {
        // 提取origin的n-gram集合
        Map<Integer, Set<String>> originNgrams = extractNgrams(originContent);
        
        for (CandidateSegment candidate : candidates) {
            Map<Integer, Set<String>> candidateNgrams = extractNgrams(candidate.getContent());
            
            // 计算命中比例
            int totalOriginNgrams = originNgrams.values().stream().mapToInt(Set::size).sum();
            if (totalOriginNgrams == 0) {
                candidate.setNgramHitRatio(0.0);
                continue;
            }
            
            int hitCount = 0;
            for (Map.Entry<Integer, Set<String>> entry : originNgrams.entrySet()) {
                int n = entry.getKey();
                Set<String> ngrams = entry.getValue();
                Set<String> candidateNgramsOfSameSize = candidateNgrams.get(n);
                
                if (candidateNgramsOfSameSize != null) {
                    for (String ngram : ngrams) {
                        if (candidateNgramsOfSameSize.contains(ngram)) {
                            hitCount++;
                            break; // 每个ngram只计一次命中
                        }
                    }
                }
            }
            
            double hitRatio = (double) hitCount / totalOriginNgrams;
            candidate.setNgramHitRatio(hitRatio);
        }
        
        log.debug("n-gram评分完成: 3-gram和4-gram命中率计算");
    }
    
    /**
     * 综合评分排序，保留Top N
     * 
     * @param candidates 候选列表
     * @return 排序后的Top N候选
     */
    private List<CandidateSegment> rankAndSelect(List<CandidateSegment> candidates) {
        // 综合评分计算
        for (int i = 0; i < candidates.size(); i++) {
            CandidateSegment candidate = candidates.get(i);
            
            double tokenScore = calculateTokenScore(candidate);
            double structureScore = candidate.getStructureSimilarity();
            double ngramScore = candidate.getNgramHitRatio();
            
            // 综合评分 = tokenScore * 0.4 + structureScore * 0.3 + ngramScore * 0.3
            double combinedScore = tokenScore * 0.4 + structureScore * 0.3 + ngramScore * 0.3;
            candidate.setDiscoverScore(combinedScore);
            candidate.setRank(i + 1);
        }
        
        // 按综合评分排序，保留Top N
        return candidates.stream()
                     .sorted((a, b) -> Double.compare(b.getDiscoverScore(), a.getDiscoverScore()))
                     .limit(config.getMaxCandidates())
                     .collect(Collectors.toList());
    }
    
    /**
     * 计算token评分
     * 
     * @param candidate 候选段
     * @return token评分（0-1）
     */
    private double calculateTokenScore(CandidateSegment candidate) {
        // 简化实现：基于命中行数占总行数的比例
        Set<String> originTokens = extractOriginTokens();
        List<Integer> candidateLines = new ArrayList<>();
        
        for (int line = candidate.getStartLine(); line <= candidate.getEndLine(); line++) {
            candidateLines.add(line);
        }
        
        int hitLines = 0;
        for (Integer line : candidateLines) {
            Set<String> lineTokens = extractTokensFromLine(targetIndex.getLine(line));
            for (String token : originTokens) {
                if (lineTokens.contains(token)) {
                    hitLines++;
                    break;
                }
            }
        }
        
        return candidateLines.isEmpty() ? 0.0 : (double) hitLines / candidateLines.size();
    }
    
    /**
     * 从单行提取tokens
     * 
     * @param line 代码行
     * @return token集合
     */
    private Set<String> extractTokensFromLine(String line) {
        Set<String> tokens = new HashSet<>();
        String[] words = line.replaceAll("[^a-zA-Z0-9_]", " ").split("\\s+");
        for (String word : words) {
            if (word.length() >= config.getMinTokenLength()) {
                tokens.add(word.toLowerCase());
            }
        }
        return tokens;
    }
    
    /**
     * 提取结构特征
     * 
     * @param content 代码内容
     * @return 结构特征
     */
    private StructureFeatures extractStructureFeatures(String content) {
        String[] lines = content.split("\n");
        
        int ifCount = 0, loopCount = 0, returnCount = 0, callCount = 0;
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // 简单的模式匹配统计
            if (trimmed.contains("if ")) ifCount++;
            if (trimmed.contains("for ") || trimmed.contains("while ") || trimmed.contains("do ")) loopCount++;
            if (trimmed.contains("return ")) returnCount++;
            if (trimmed.matches(".*\\w+\\(.*\\).*")) callCount++; // 简单的方法调用检测
        }
        
        return StructureFeatures.builder()
                .lineCount(lines.length)
                .ifCount(ifCount)
                .loopCount(loopCount)
                .returnCount(returnCount)
                .callCount(callCount)
                .build();
    }
    
    /**
     * 提取n-gram（n=3,4）
     * 
     * @param content 代码内容
     * @return n-gram映射
     */
    private Map<Integer, Set<String>> extractNgrams(String content) {
        Map<Integer, Set<String>> ngrams = new HashMap<>();
        
        for (int n : Arrays.asList(3, 4)) {
            Set<String> ngramSet = new HashSet<>();
            String[] lines = content.split("\n");
            
            for (String line : lines) {
                String normalized = line.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
                if (normalized.length() >= n) {
                    for (int i = 0; i <= normalized.length() - n; i++) {
                        ngramSet.add(normalized.substring(i, i + n));
                    }
                }
            }
            
            ngrams.put(n, ngramSet);
        }
        
        return ngrams;
    }
    
    /**
     * 发现阶段配置
     */
    public static class DiscoverConfig {
        private int minTokenLength = 4;
        private int minTokenHit = 2;
        private int expandRadius = 10;
        private int maxCandidates = 10;
        private int maxLinesForNgram = 20000;
        private double structureTolerance = 0.4;
        
        // Getters and setters
        public int getMinTokenLength() { return minTokenLength; }
        public void setMinTokenLength(int minTokenLength) { this.minTokenLength = minTokenLength; }
        
        public int getMinTokenHit() { return minTokenHit; }
        public void setMinTokenHit(int minTokenHit) { this.minTokenHit = minTokenHit; }
        
        public int getExpandRadius() { return expandRadius; }
        public void setExpandRadius(int expandRadius) { this.expandRadius = expandRadius; }
        
        public int getMaxCandidates() { return maxCandidates; }
        public void setMaxCandidates(int maxCandidates) { this.maxCandidates = maxCandidates; }
        
        public int getMaxLinesForNgram() { return maxLinesForNgram; }
        public void setMaxLinesForNgram(int maxLinesForNgram) { this.maxLinesForNgram = maxLinesForNgram; }
        
        public double getStructureTolerance() { return structureTolerance; }
        public void setStructureTolerance(double structureTolerance) { this.structureTolerance = structureTolerance; }
    }
    
    /**
     * 结构特征
     */
    @lombok.Builder
    @lombok.Data
    public static class StructureFeatures {
        private int lineCount;
        private int ifCount;
        private int loopCount;
        private int returnCount;
        private int callCount;
    }
}
