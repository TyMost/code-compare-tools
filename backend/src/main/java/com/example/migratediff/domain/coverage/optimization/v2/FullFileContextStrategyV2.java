package com.example.migratediff.domain.coverage.optimization.v2;

import com.example.migratediff.domain.coverage.optimization.*;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full File Coverage Optimization v2
 * Discover → Verify 双阶段覆盖率优化算法
 * 
 * 核心改进：
 * 1. 从"相似度证明"到"覆盖率发现"的哲学转变
 * 2. Token锚点索引解决适配改造问题（Mapper→DAO）
 * 3. 结构轮廓过滤增强鲁棒性
 * 4. 明确的性能控制和超时机制
 */
@Slf4j
@Component("fullFileContextStrategyV2")
public class FullFileContextStrategyV2 implements CoverageOptimizationStrategy {
    
    private final GitRepositoryHelper gitRepositoryHelper;
    private final TimeoutManager timeoutManager;
    
    // 配置参数
    @Value("${coverage.optimization.full-file-context-v2.enabled:true}")
    private boolean enabled;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.enabled:true}")
    private boolean discoverEnabled;
    
    @Value("${coverage.optimization.full-file-context-v2.verify.enabled:true}")
    private boolean verifyEnabled;
    
    @Value("${coverage.optimization.full-file-context-v2.performance.skip-large-files:true}")
    private boolean skipLargeFiles;
    
    @Value("${coverage.optimization.full-file-context-v2.performance.max-file-lines:50000}")
    private int maxFileLines;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.min-token-length:4}")
    private int minTokenLength;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.min-token-hit:2}")
    private int minTokenHit;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.expand-radius:10}")
    private int expandRadius;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.max-candidates:10}")
    private int maxCandidates;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.max-lines-for-ngram:20000}")
    private int maxLinesForNgram;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.structure-tolerance:0.4}")
    private double structureTolerance;
    
    @Value("${coverage.optimization.full-file-context-v2.verify.max-candidates-to-verify:3}")
    private int maxCandidatesToVerify;
    
    @Value("${coverage.optimization.full-file-context-v2.verify.hit-threshold:0.8}")
    private double hitThreshold;
    
    @Value("${coverage.optimization.full-file-context-v2.verify.weak-hit-threshold:0.4}")
    private double weakHitThreshold;
    
    @Value("${coverage.optimization.full-file-context-v2.discover.timeout-ms:3000}")
    private long discoverTimeoutMs;
    
    @Value("${coverage.optimization.full-file-context-v2.verify.timeout-ms:1000}")
    private long verifyTimeoutMs;
    
    @Value("${coverage.optimization.full-file-context-v2.performance.total-timeout-ms:5000}")
    private long totalTimeoutMs;
    
    @Autowired
    public FullFileContextStrategyV2(GitRepositoryHelper gitRepositoryHelper) {
        this.gitRepositoryHelper = gitRepositoryHelper;
        this.timeoutManager = new TimeoutManager(createPerformanceConfig());
    }
    
    @Override
    public String getStrategyName() {
        return "FULL_FILE_CONTEXT_V2";
    }
    
    @Override
    public int getPriority() {
        return 2; // 保持与原策略相同的优先级
    }
    
    @Override
    public boolean supports(OptimizationContext context) {
        // 基本开关检查
        if (!enabled) {
            log.debug("V2策略已禁用");
            return false;
        }
        
        // 发现阶段检查
        if (!discoverEnabled && !verifyEnabled) {
            log.debug("V2发现和验证阶段都已禁用");
            return false;
        }
        
        // 性能检查：过大文件跳过
        if (skipLargeFiles && shouldSkipLargeFile(context)) {
            log.debug("文件过大，跳过V2优化");
            return false;
        }
        
        // 原有支持逻辑（与v1兼容）
        return context.isDiscoveryMode() || 
               (context.getCurrentSimilarity() < 1.0 && 
                hasGitInfo(context));
    }
    
    @Override
    public OptimizationResult optimize(OptimizationContext context) {
        long totalStartTime = System.currentTimeMillis();
        
        try {
            log.debug("开始V2优化: similarity={}, discoveryMode={}", 
                     context.getCurrentSimilarity(), context.isDiscoveryMode());
            
            // 1. 获取完整文件内容
            String targetFileContent = getTargetFileContent(context);
            if (targetFileContent == null) {
                return createFallbackResult(context, "无法获取目标文件内容");
            }
            
            // 2. 获取origin内容
            String originContent = combineBlockContent(context.getOriginBlock());
            
            // 3. 执行Discover阶段
            List<CandidateSegment> candidates = null;
            long discoverTime = 0;
            
            if (discoverEnabled) {
                DiscoverPhase.DiscoverConfig discoverConfig = createDiscoverConfig();
                DiscoverPhase discoverPhase = new DiscoverPhase(targetFileContent, originContent, discoverConfig);
                candidates = discoverPhase.discoverCandidates();
                discoverTime = System.currentTimeMillis() - totalStartTime;
                
                if (candidates.isEmpty()) {
                    return createFallbackResult(context, "未发现任何候选代码段");
                }
            } else {
                // 如果禁用发现阶段，创建空候选列表
                candidates = new ArrayList<>();
            }
            
            // 4. 执行Verify阶段
            CoverageResult coverageResult = null;
            long verifyTime = 0;
            
            if (verifyEnabled && !candidates.isEmpty()) {
                VerifyPhase.VerifyConfig verifyConfig = createVerifyConfig();
                VerifyPhase verifyPhase = new VerifyPhase(originContent, candidates, verifyConfig);
                coverageResult = verifyPhase.verifyCoverage();
                verifyTime = System.currentTimeMillis() - totalStartTime - discoverTime;
            } else {
                // 创建默认覆盖率结果
                coverageResult = CoverageResult.builder()
                        .grade(CoverageResult.CoverageGrade.MISS)
                        .coverageRate(0.0)
                        .bestCandidate(candidates.isEmpty() ? null : candidates.get(0))
                        .candidates(candidates)
                        .discoverTimeMs(discoverTime)
                        .verifyTimeMs(0)
                        .build();
            }
            
            // 5. 转换为OptimizationResult
            long totalTime = System.currentTimeMillis() - totalStartTime;
            return convertToOptimizationResult(context, coverageResult, totalTime);
            
        } catch (Exception e) {
            log.warn("V2算法执行失败: {}", e.getMessage(), e);
            return createFallbackResult(context, "V2算法执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取目标文件完整内容
     */
    private String getTargetFileContent(OptimizationContext context) {
        try {
            String targetFilePath = context.getTargetFile().getRelativePath();
            String targetRepoPath = context.getTargetFile().getRepoPath();
            String commitHash = context.getTargetFile().getCommitHash();
            
            // 使用与原策略相同的方法获取文件内容
            return getCompleteFileContent(targetFilePath, commitHash, targetRepoPath);
        } catch (Exception e) {
            log.warn("获取目标文件内容失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取指定提交的完整文件内容
     * 复用现有的GitRepositoryHelper
     */
    private String getCompleteFileContent(String filePath, String commitHash, String repoPath) {
        try {
            // 创建临时的RepoConfig来使用GitRepositoryHelper
            com.example.migratediff.domain.repo.RepoPath tempRepoPath = com.example.migratediff.domain.repo.RepoPath.builder()
                .absolutePath(repoPath)
                .type(null) // 可以为null，因为主要用absolutePath
                .build();
            
            com.example.migratediff.domain.repo.RepoConfig tempRepoConfig = com.example.migratediff.domain.repo.RepoConfig.builder()
                .repoPath(tempRepoPath)
                .build();
            
            org.eclipse.jgit.lib.Repository repository = gitRepositoryHelper.openRepository(tempRepoConfig);
            
            try {
                // 解析提交
                org.eclipse.jgit.lib.ObjectId commitId = repository.resolve(commitHash);
                org.eclipse.jgit.revwalk.RevCommit commit = new org.eclipse.jgit.revwalk.RevWalk(repository).parseCommit(commitId);
                
                // 获取文件内容
                org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(repository);
                try {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath));
                    
                    if (treeWalk.next()) {
                        org.eclipse.jgit.lib.ObjectId objectId = treeWalk.getObjectId(0);
                        org.eclipse.jgit.lib.ObjectLoader loader = repository.open(objectId);
                        return new String(loader.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    // 如果没有找到文件，返回null
                    return null;
                } finally {
                    treeWalk.close();
                }
            } finally {
                gitRepositoryHelper.closeRepository(repository);
            }
        } catch (Exception e) {
            log.warn("获取完整文件内容失败: {} @ {} in {}", filePath, commitHash, repoPath, e);
            return null;
        }
    }
    
    /**
     * 合并差异块内容
     */
    private String combineBlockContent(DiffBlock block) {
        return block != null && block.getContentFrom() != null ? block.getContentFrom() : "";
    }
    
    /**
     * 检查是否应该跳过大文件
     */
    private boolean shouldSkipLargeFile(OptimizationContext context) {
        try {
            String targetContent = getTargetFileContent(context);
            if (targetContent != null) {
                int lineCount = targetContent.split("\n").length;
                return lineCount > maxFileLines;
            }
        } catch (Exception e) {
            log.debug("检查文件大小时出错，不跳过: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 检查是否有Git信息
     */
    private boolean hasGitInfo(OptimizationContext context) {
        return context.getTargetFile() != null && 
               context.getTargetFile().getRepoPath() != null &&
               context.getTargetFile().getRelativePath() != null;
    }
    
    /**
     * 创建发现阶段配置
     */
    private DiscoverPhase.DiscoverConfig createDiscoverConfig() {
        DiscoverPhase.DiscoverConfig config = new DiscoverPhase.DiscoverConfig();
        config.setMinTokenLength(minTokenLength);
        config.setMinTokenHit(minTokenHit);
        config.setExpandRadius(expandRadius);
        config.setMaxCandidates(maxCandidates);
        config.setMaxLinesForNgram(maxLinesForNgram);
        config.setStructureTolerance(structureTolerance);
        return config;
    }
    
    /**
     * 创建验证阶段配置
     */
    private VerifyPhase.VerifyConfig createVerifyConfig() {
        VerifyPhase.VerifyConfig config = new VerifyPhase.VerifyConfig();
        config.setMaxCandidatesToVerify(maxCandidatesToVerify);
        config.setHitThreshold(hitThreshold);
        config.setWeakHitThreshold(weakHitThreshold);
        config.setTimeoutMs(verifyTimeoutMs);
        config.setEarlyStopEnabled(true);
        return config;
    }
    
    /**
     * 创建性能配置
     */
    private TimeoutManager.PerformanceConfig createPerformanceConfig() {
        TimeoutManager.PerformanceConfig config = new TimeoutManager.PerformanceConfig();
        config.setDiscoverTimeoutMs(discoverTimeoutMs);
        config.setVerifyTimeoutMs(verifyTimeoutMs);
        config.setTotalTimeoutMs(totalTimeoutMs);
        return config;
    }
    
    /**
     * 转换为OptimizationResult
     */
    private OptimizationResult convertToOptimizationResult(OptimizationContext context, 
                                                   CoverageResult coverageResult, 
                                                   long executionTime) {
        // 构建details
        Map<String, Object> details = new HashMap<>();
        details.put("algorithmVersion", "v2");
        details.put("coverageGrade", coverageResult.getGrade().name());
        details.put("coverageRate", coverageResult.getCoverageRate());
        details.put("discoverTimeMs", coverageResult.getDiscoverTimeMs());
        details.put("verifyTimeMs", coverageResult.getVerifyTimeMs());
        details.put("totalCandidates", coverageResult.getCandidates().size());
        details.put("timeout", coverageResult.isTimeout());
        details.put("executionTimeMs", executionTime);
        
        if (coverageResult.getBestCandidate() != null) {
            details.put("bestCandidateStartLine", coverageResult.getBestCandidate().getStartLine());
            details.put("bestCandidateEndLine", coverageResult.getBestCandidate().getEndLine());
            details.put("bestCandidateScore", coverageResult.getBestCandidate().getDiscoverScore());
            details.put("bestCandidateCoverage", coverageResult.getBestCandidate().getVerifyCoverage());
        }
        
        // 根据覆盖等级确定相似度提升
        double newSimilarity = calculateNewSimilarity(context.getCurrentSimilarity(), coverageResult);
        
        if (newSimilarity > context.getCurrentSimilarity()) {
            log.debug("V2优化成功: 原相似度={}, 新相似度={}, 等级={}", 
                      context.getCurrentSimilarity(), newSimilarity, coverageResult.getGrade());
            
            return OptimizationResult.createOptimized(
                context.getCurrentSimilarity(),
                newSimilarity,
                "V2覆盖率优化: " + coverageResult.getGrade() + 
                " (覆盖率: " + String.format("%.2f", coverageResult.getCoverageRate() * 100) + "%)",
                details,
                executionTime
            );
        } else {
            log.debug("V2优化未提升相似度: 原相似度={}, 新相似度={}", 
                      context.getCurrentSimilarity(), newSimilarity);
            
            return OptimizationResult.notOptimized(
                context.getCurrentSimilarity(),
                "V2覆盖率优化未提升相似度: " + coverageResult.getGrade() + 
                " (覆盖率: " + String.format("%.2f", coverageResult.getCoverageRate() * 100) + "%)"
            );
        }
    }
    
    /**
     * 根据覆盖等级调整相似度
     */
    private double calculateNewSimilarity(double currentSimilarity, CoverageResult coverageResult) {
        switch (coverageResult.getGrade()) {
            case HIT:
                return Math.max(currentSimilarity, 0.9); // 高覆盖提升至0.9
            case WEAK_HIT:
                return Math.max(currentSimilarity, 0.6); // 弱覆盖提升至0.6
            case MISS:
                return currentSimilarity; // 未覆盖保持原样
            default:
                return currentSimilarity;
        }
    }
    
    /**
     * 创建回退结果
     */
    private OptimizationResult createFallbackResult(OptimizationContext context, String reason) {
        log.debug("V2优化回退: {}", reason);
        
        Map<String, Object> details = new HashMap<>();
        details.put("algorithmVersion", "v2");
        details.put("fallbackReason", reason);
        
        return OptimizationResult.notOptimized(
            context.getCurrentSimilarity(),
            "V2优化回退: " + reason
        );
    }
}
