package com.example.migratediff.domain.coverage.optimization.v2;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 验证阶段
 * 计算origin内容是否被candidate覆盖
 */
@Slf4j
public class VerifyPhase {
    
    private final String originContent;
    private final List<CandidateSegment> candidates;
    private final VerifyConfig config;
    
    /**
     * 构造函数
     * 
     * @param originContent origin内容
     * @param candidates 候选代码段列表
     * @param config 验证阶段配置
     */
    public VerifyPhase(String originContent, List<CandidateSegment> candidates, VerifyConfig config) {
        this.originContent = originContent;
        this.candidates = candidates;
        this.config = config;
    }
    
    /**
     * 执行验证阶段
     * 
     * @return 覆盖率结果
     */
    public CoverageResult verifyCoverage() {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("开始验证阶段: 候选数量={}", candidates.size());
            
            // 只验证Top N候选（性能优化）
            List<CandidateSegment> candidatesToVerify = candidates.stream()
                .limit(config.getMaxCandidatesToVerify())
                .collect(Collectors.toList());
            
            CandidateSegment bestCandidate = null;
            double bestCoverage = 0.0;
            
            for (int i = 0; i < candidatesToVerify.size(); i++) {
                CandidateSegment candidate = candidatesToVerify.get(i);
                
                // 早停检查：剩余最大可能覆盖 < HIT阈值则停止
                if (config.isEarlyStopEnabled() && shouldEarlyStop(bestCoverage, i, candidatesToVerify.size())) {
                    log.debug("早停触发: 当前最佳覆盖率={}, 剩余候选数={}", bestCoverage, 
                               candidatesToVerify.size() - i - 1);
                    break;
                }
                
                double coverage = calculateLineCoverage(originContent, candidate.getContent());
                candidate.setVerifyCoverage(coverage);
                
                if (coverage > bestCoverage) {
                    bestCoverage = coverage;
                    bestCandidate = candidate;
                }
                
                log.debug("候选{}验证完成: 覆盖率={}", candidate.getDescription(), coverage);
                
                // 检查超时
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > config.getTimeoutMs()) {
                    log.warn("验证阶段超时，已验证{}/{}个候选", i + 1, candidatesToVerify.size());
                    break;
                }
            }
            
            // 确定覆盖等级
            CoverageResult.CoverageGrade grade = determineCoverageGrade(bestCoverage);
            
            // 确保即使MISS也返回最优候选
            if (bestCandidate == null && !candidates.isEmpty()) {
                bestCandidate = candidates.get(0);
                bestCoverage = 0.0;
                grade = CoverageResult.CoverageGrade.MISS;
            }
            
            long verifyTime = System.currentTimeMillis() - startTime;
            
            CoverageResult result = CoverageResult.builder()
                    .grade(grade)
                    .coverageRate(bestCoverage)
                    .bestCandidate(bestCandidate)
                    .candidates(candidates)
                    .verifyTimeMs(verifyTime)
                    .totalCandidatesDiscovered(candidates.size())
                    .candidatesAfterFilter(candidates.size())
                    .timeout(verifyTime > config.getTimeoutMs())
                    .build();
            
            log.debug("验证阶段完成: 等级={}, 覆盖率={}, 最优候选={}, 耗时={}ms", 
                     grade, bestCoverage, 
                     bestCandidate != null ? bestCandidate.getDescription() : "无", verifyTime);
            
            return result;
            
        } catch (Exception e) {
            log.warn("验证阶段执行失败", e);
            // 即使失败也返回最优候选
            return CoverageResult.builder()
                    .grade(CoverageResult.CoverageGrade.MISS)
                    .coverageRate(0.0)
                    .bestCandidate(candidates.isEmpty() ? null : candidates.get(0))
                    .candidates(candidates)
                    .verifyTimeMs(System.currentTimeMillis() - startTime)
                    .timeout(true)
                    .build();
        }
    }
    
    /**
     * 计算行级覆盖率
     * 归一化origin/candidate行，使用HashSet判断覆盖
     * 
     * @param originContent origin内容
     * @param candidateContent 候选内容
     * @return 覆盖率（0-1）
     */
    private double calculateLineCoverage(String originContent, String candidateContent) {
        // 归一化行：去除空白、注释等噪音
        Set<String> originLines = normalizeLines(originContent);
        Set<String> candidateLines = normalizeLines(candidateContent);
        
        if (originLines.isEmpty()) {
            return 0.0;
        }
        
        // 计算覆盖
        int covered = 0;
        for (String originLine : originLines) {
            if (candidateLines.contains(originLine)) {
                covered++;
            }
        }
        
        double coverage = (double) covered / originLines.size();
        log.debug("行级覆盖率计算: origin行={}, candidate行={}, 覆盖={}/{}, 覆盖率={}", 
                  originLines.size(), candidateLines.size(), covered, originLines.size(), coverage);
        
        return coverage;
    }
    
    /**
     * 归一化行内容
     * 去除空白、注释等噪音
     * 
     * @param content 原始内容
     * @return 归一化后的行集合
     */
    private Set<String> normalizeLines(String content) {
        Set<String> normalizedLines = new HashSet<>();
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            
            // 跳过空行
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // 跳过纯注释行
            if (isCommentLine(trimmed)) {
                continue;
            }
            
            // 去除行内注释和多余空格
            String normalized = removeInlineComments(trimmed);
            normalizedLines.add(normalized);
        }
        
        return normalizedLines;
    }
    
    /**
     * 检查是否为注释行
     * 
     * @param line 代码行
     * @return 是否为注释行
     */
    private boolean isCommentLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || 
               trimmed.startsWith("/*") || 
               trimmed.startsWith("*") || 
               trimmed.startsWith("*/") ||
               trimmed.matches("\\s*\\*.*") ||
               trimmed.matches("\\s*//.*");
    }
    
    /**
     * 去除行内注释
     * 
     * @param line 代码行
     * @return 去除注释后的行
     */
    private String removeInlineComments(String line) {
        // 简单实现：去除//之后的注释
        int commentIndex = line.indexOf("//");
        if (commentIndex >= 0) {
            return line.substring(0, commentIndex).trim();
        }
        
        // 简单实现：去除/* */之间的注释
        int startComment = line.indexOf("/*");
        int endComment = line.indexOf("*/");
        if (startComment >= 0 && endComment > startComment) {
            return (line.substring(0, startComment) + line.substring(endComment + 2)).trim();
        }
        
        return line.trim();
    }
    
    /**
     * 确定覆盖等级
     * 
     * @param coverage 覆盖率
     * @return 覆盖等级
     */
    private CoverageResult.CoverageGrade determineCoverageGrade(double coverage) {
        if (coverage >= config.getHitThreshold()) {
            return CoverageResult.CoverageGrade.HIT;
        } else if (coverage >= config.getWeakHitThreshold()) {
            return CoverageResult.CoverageGrade.WEAK_HIT;
        } else {
            return CoverageResult.CoverageGrade.MISS;
        }
    }
    
    /**
     * 早停检查
     * 若剩余最大可能覆盖 < HIT阈值，提前终止
     * 
     * @param currentBest 当前最佳覆盖率
     * @param currentIndex 当前验证索引
     * @param totalCandidates 总候选数
     * @return 是否应该早停
     */
    private boolean shouldEarlyStop(double currentBest, int currentIndex, int totalCandidates) {
        if (!config.isEarlyStopEnabled()) {
            return false;
        }
        
        // 计算剩余候选数
        int remainingCandidates = totalCandidates - currentIndex - 1;
        
        // 假设剩余候选都能达到1.0覆盖率，最佳可能结果
        double possibleBest = Math.max(currentBest, 1.0);
        
        // 如果即使剩余候选都完美，也无法达到HIT阈值，则早停
        return possibleBest < config.getHitThreshold();
    }
    
    /**
     * 验证阶段配置
     */
    public static class VerifyConfig {
        private boolean enabled = true;
        private int maxCandidatesToVerify = 3;
        private double hitThreshold = 0.8;
        private double weakHitThreshold = 0.4;
        private long timeoutMs = 1000;
        private boolean earlyStopEnabled = true;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getMaxCandidatesToVerify() { return maxCandidatesToVerify; }
        public void setMaxCandidatesToVerify(int maxCandidatesToVerify) { this.maxCandidatesToVerify = maxCandidatesToVerify; }
        
        public double getHitThreshold() { return hitThreshold; }
        public void setHitThreshold(double hitThreshold) { this.hitThreshold = hitThreshold; }
        
        public double getWeakHitThreshold() { return weakHitThreshold; }
        public void setWeakHitThreshold(double weakHitThreshold) { this.weakHitThreshold = weakHitThreshold; }
        
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        
        public boolean isEarlyStopEnabled() { return earlyStopEnabled; }
        public void setEarlyStopEnabled(boolean earlyStopEnabled) { this.earlyStopEnabled = earlyStopEnabled; }
    }
}
