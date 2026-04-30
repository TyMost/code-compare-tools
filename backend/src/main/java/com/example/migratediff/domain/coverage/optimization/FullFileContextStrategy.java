package com.example.migratediff.domain.coverage.optimization;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 完整文件上下文策略
 * 通过获取完整文件内容来优化相似度计算
 * 解决Git Diff片段化信息不足的问题
 */
@Slf4j
@Component
public class FullFileContextStrategy implements CoverageOptimizationStrategy {
    
    private final GitRepositoryHelper gitRepositoryHelper;
    
    @Value("${coverage.optimization.full-file-context.max-file-size:1048576}")
    private long maxFileSize;
    
    @Value("${coverage.optimization.full-file-context.sample-radius:50}")
    private int sampleRadius;
    
    @Value("${coverage.optimization.full-file-context.timeout-ms:5000}")
    private long timeoutMs;
    
    @Value("${coverage.optimization.full-file-context.update-content:false}")
    private boolean updateContent;
    
    @Value("${coverage.optimization.full-file-context.update-threshold:0.1}")
    private double updateThreshold;
    
    @Value("${coverage.optimization.full-file-context.parallel-search:true}")
    private boolean parallelSearch;
    
    @Value("${coverage.optimization.full-file-context.parallel-threads:4}")
    private int parallelThreads;
    
    @Value("${coverage.optimization.full-file-context.index-threshold:50000}")
    private int indexThreshold;
    
    @Value("${coverage.optimization.full-file-context.fuzzy-threshold:0.8}")
    private double fuzzyThreshold;
    
    @Value("${coverage.optimization.full-file-context.context-expansion:method}")
    private String contextExpansionStrategy;
    
    // 发现模式配置参数
    @Value("${coverage.optimization.full-file-context.discovery.enabled:true}")
    private boolean discoveryEnabled;
    
    @Value("${coverage.optimization.full-file-context.discovery.confidence-threshold:0.7}")
    private double discoveryConfidenceThreshold;
    
    @Value("${coverage.optimization.full-file-context.discovery.min-content-length:50}")
    private int discoveryMinContentLength;
    
    @Value("${coverage.optimization.full-file-context.discovery.timeout-ms:3000}")
    private long discoveryTimeoutMs;
    
    @Autowired
    public FullFileContextStrategy(GitRepositoryHelper gitRepositoryHelper) {
        this.gitRepositoryHelper = gitRepositoryHelper;
    }
    
    @Override
    public String getStrategyName() {
        return "FULL_FILE_CONTEXT";
    }
    
    @Override
    public int getPriority() {
        return 2; // 在噪音过滤（优先级1）之后执行
    }
    
    @Override
    public boolean supports(OptimizationContext context) {
        // 检查是否为发现模式
        if (context.isDiscoveryMode()) {
            // 发现模式：需要启用发现功能且有Git信息
            if (!discoveryEnabled) {
                return false;
            }
            
            return context.getAdditionalData() != null
                    && context.getAdditionalData().get("targetCommitHash") != null
                    && context.getAdditionalData().get("targetRepoPath") != null
                    && context.getOriginFile() != null
                    && context.getOriginFile().getRelativePath() != null;
        }
        
        // 优化模式：只处理非完美匹配
        if (context.getCurrentSimilarity() >= 1.0) {
            return false;
        }
        
        // 检查是否有完整的Git信息（优先使用）
        if (context.getAdditionalData() != null
                && context.getAdditionalData().get("originCommitHash") != null
                && context.getAdditionalData().get("targetCommitHash") != null
                && context.getAdditionalData().get("originRepoPath") != null
                && context.getAdditionalData().get("targetRepoPath") != null) {
            return true;
        }
        
        // 如果没有Git信息，但有文件路径，也可以尝试降级处理
        return context.getOriginFile() != null 
                && context.getTargetFile() != null
                && context.getOriginFile().getRelativePath() != null
                && context.getTargetFile().getRelativePath() != null;
    }
    
    @Override
    public OptimizationResult optimize(OptimizationContext context) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 检查是否为发现模式
            if (context.isDiscoveryMode()) {
                return discoverMatch(context, startTime);
            }
            
            // 优化模式：检查是否有完整的Git信息
            Map<String, Object> additionalData = context.getAdditionalData();
            boolean hasFullGitInfo = additionalData != null
                    && additionalData.get("originCommitHash") != null
                    && additionalData.get("targetCommitHash") != null
                    && additionalData.get("originRepoPath") != null
                    && additionalData.get("targetRepoPath") != null;
            
            if (hasFullGitInfo) {
                return optimizeWithFullGitInfo(context, additionalData, startTime);
            } else {
                // 如果没有Git信息，跳过优化
                long executionTime = System.currentTimeMillis() - startTime;
                return OptimizationResult.notOptimized(
                    context.getCurrentSimilarity(),
                    "缺少Git信息，跳过完整文件上下文优化"
                );
            }
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.warn("完整文件上下文优化失败: {}", e.getMessage(), e);
            
            return OptimizationResult.notOptimized(
                context.getCurrentSimilarity(),
                "完整文件上下文优化失败: " + e.getMessage()
            );
        }
    }
    
    /**
     * 使用完整Git信息进行优化
     */
    private OptimizationResult optimizeWithFullGitInfo(OptimizationContext context, 
                                                      Map<String, Object> additionalData, 
                                                      long startTime) {
        String originCommitHash = (String) additionalData.get("originCommitHash");
        String targetCommitHash = (String) additionalData.get("targetCommitHash");
        String targetRepoPath = (String) additionalData.get("targetRepoPath");
        
        // 从文件对象获取路径
        String originFilePath = context.getOriginFile().getRelativePath();
        String targetFilePath = context.getTargetFile().getRelativePath();
        
        log.debug("开始完整文件上下文优化: {} @ {} -> {} @ {}", 
                 originFilePath, originCommitHash, targetFilePath, targetCommitHash);
        
        // 获取完整文件内容（只获取target文件内容）
        String targetFileContent = getCompleteFileContent(targetFilePath, targetCommitHash, targetRepoPath);
        
        if (targetFileContent == null) {
            return OptimizationResult.notOptimized(
                    context.getCurrentSimilarity(),
                    "缺少Git信息，跳过完整文件上下文优化"
            );
        }
        
        return performOptimizationWithFileContent(context, targetFileContent, 
                                                 originFilePath, targetFilePath, startTime);
    }
    
    
    /**
     * 获取指定提交的完整文件内容
     * 复用现有的GitRepositoryHelper
     */
    private String getCompleteFileContent(String filePath, String commitHash, String repoPath) {
        try {
            // 创建临时的RepoConfig来使用GitRepositoryHelper
            RepoPath tempRepoPath = RepoPath.builder()
                .absolutePath(repoPath)
                .type(null) // 可以为null，因为主要用absolutePath
                .build();
            
            RepoConfig tempRepoConfig = RepoConfig.builder()
                .repoPath(tempRepoPath)
                .build();
            
            Repository repository = gitRepositoryHelper.openRepository(tempRepoConfig);
            
            try {
                // 解析提交
                ObjectId commitId = repository.resolve(commitHash);
                RevCommit commit = new RevWalk(repository).parseCommit(commitId);
                
                // 获取文件内容
                TreeWalk treeWalk = new TreeWalk(repository);
                try {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(filePath));
                    
                    if (treeWalk.next()) {
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectLoader loader = repository.open(objectId);
                        return new String(loader.getBytes(), StandardCharsets.UTF_8);
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
     * 处理大文件，避免内存溢出
     */
    private String handleLargeFile(String fullContent, DiffBlock targetBlock) {
        if (fullContent != null && fullContent.length() > maxFileSize) {
            // 智能采样：只保留目标块周围的上下文
            return sampleAroundTargetBlock(fullContent, targetBlock);
        }
        return fullContent;
    }
    
    /**
     * 在完整文件中找到最佳匹配的代码段
     */
    private CodeSegment findBestMatchingSegment(String completeFile, DiffBlock targetBlock) {
        String targetContent = combineBlockContent(targetBlock);
        
        // 策略1：基于原始行号定位（最直接）
        int expectedStartLine = targetBlock.getStartLineFrom();
        int expectedEndLine = targetBlock.getEndLineFrom();
        
        List<String> lines = Arrays.asList(completeFile.split("\n"));
        
        // 验证行号有效性
        if (expectedStartLine > 0 && expectedEndLine <= lines.size()) {
            String directMatch = String.join("\n", 
                lines.subList(expectedStartLine - 1, expectedEndLine));
            
            double directSimilarity = calculateBaseSimilarity(directMatch, targetContent);
            
            // 如果直接匹配度很高，直接返回
            if (directSimilarity > 0.8) {
                return new CodeSegment(expectedStartLine, expectedEndLine, directMatch);
            }
        }
        
        // 策略2：滑动窗口搜索（当行号不准确时）
        return slidingWindowSearch(completeFile, targetContent);
    }
    
    /**
     * 滑动窗口搜索最佳匹配
     */
    private CodeSegment slidingWindowSearch(String completeFile, String targetContent) {
        List<String> lines = Arrays.asList(completeFile.split("\n"));
        double bestSimilarity = 0.0;
        int bestStartLine = 1;
        int bestEndLine = Math.min(lines.size(), 50); // 默认搜索前50行
        
        // 滑动窗口，窗口大小可配置
        for (int startLine = 0; startLine < Math.max(0, lines.size() - 20); startLine++) {
            int endLine = Math.min(startLine + 50, lines.size());
            
            String segmentContent = String.join("\n", lines.subList(startLine, endLine));
            double similarity = calculateBaseSimilarity(segmentContent, targetContent);
            
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestStartLine = startLine + 1; // 转换为1基行号
                bestEndLine = endLine;
            }
        }
        
        return new CodeSegment(bestStartLine, bestEndLine, 
            String.join("\n", lines.subList(bestStartLine - 1, bestEndLine)));
    }
    
    /**
     * 使用文件内容执行优化的核心逻辑（新的单向覆盖检测算法）
     */
    private OptimizationResult performOptimizationWithFileContent(OptimizationContext context,
                                                                 String targetFileContent,
                                                                 String originFilePath,
                                                                 String targetFilePath,
                                                                 long startTime) {
        try {
            // 检查文件大小，避免内存溢出
            targetFileContent = handleLargeFile(targetFileContent, context.getTargetBlock());
            
            // 获取origin diff块内容作为查询
            String originDiffContent = combineBlockContent(context.getOriginBlock());
            
            // 在target完整文件中搜索最佳匹配
            MatchResult bestMatch = findBestCoverageMatch(originDiffContent, targetFileContent);
            
            // 计算覆盖相似度
            double coverageSimilarity = calculateCoverageSimilarity(originDiffContent, bestMatch.getContent());
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (coverageSimilarity > context.getCurrentSimilarity()) {
                // 条件性更新内容
                boolean contentUpdated = false;
                if (updateContent && (coverageSimilarity - context.getCurrentSimilarity()) > updateThreshold) {
                    // 显著改进时更新g库内容
                    context.getTargetBlock().setContentTo(bestMatch.getContent());
                    contentUpdated = true;
                    bestMatch.setContentUpdated(true);
                }
                
                Map<String, Object> details = new HashMap<>();
                details.put("originContentLength", originDiffContent.length());
                details.put("matchedContentLength", bestMatch.getContentLength());
                details.put("originFilePath", originFilePath);
                details.put("targetFilePath", targetFilePath);
                details.put("improvement", coverageSimilarity - context.getCurrentSimilarity());
                details.put("matchType", bestMatch.getMatchType().toString());
                details.put("confidence", bestMatch.getConfidence());
                details.put("contentUpdated", contentUpdated);
                details.put("targetSampled", targetFileContent.length() < bestMatch.getContentLength());
                
                String optimizationType = "基于完整文件的单向覆盖优化";
                
                return OptimizationResult.createOptimized(
                    context.getCurrentSimilarity(),
                    coverageSimilarity,
                    optimizationType,
                    details,
                    executionTime
                );
            } else {
                return OptimizationResult.notOptimized(
                    context.getCurrentSimilarity(),
                    "单向覆盖检测未能提升相似度"
                );
            }
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.warn("单向覆盖检测优化失败: {}", e.getMessage(), e);
            
            return OptimizationResult.notOptimized(
                context.getCurrentSimilarity(),
                "单向覆盖检测优化失败: " + e.getMessage()
            );
        }
    }
    
    /**
     * 单向覆盖检测：在target文件中搜索origin内容的最佳匹配
     */
    private MatchResult findBestCoverageMatch(String originContent, String targetFullContent) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 第一层：快速排除
            if (shouldFastReject(originContent, targetFullContent)) {
                return MatchResult.builder()
                    .matchType(MatchType.NONE)
                    .confidence(0.0)
                    .matchTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            }
            
            // 第二层：精确匹配
            MatchResult exactMatch = findExactMatch(originContent, targetFullContent);
            if (exactMatch.hasMatch()) {
                exactMatch.setMatchTimeMs(System.currentTimeMillis() - startTime);
                return exactMatch;
            }
            
            // 第三层：模糊匹配
            MatchResult fuzzyMatch = findFuzzyMatch(originContent, targetFullContent);
            if (fuzzyMatch.getConfidence() >= fuzzyThreshold) {
                fuzzyMatch.setMatchTimeMs(System.currentTimeMillis() - startTime);
                return fuzzyMatch;
            }
            
            // 第四层：上下文扩展匹配
            MatchResult contextMatch = findContextMatch(originContent, targetFullContent, fuzzyMatch);
            contextMatch.setMatchTimeMs(System.currentTimeMillis() - startTime);
            return contextMatch;
            
        } catch (Exception e) {
            log.warn("搜索最佳匹配时发生错误: {}", e.getMessage(), e);
            return MatchResult.builder()
                .matchType(MatchType.NONE)
                .confidence(0.0)
                .matchTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    /**
     * 快速排除条件
     */
    private boolean shouldFastReject(String originContent, String targetFullContent) {
        // 1. target文件太小，不可能包含origin内容
        if (targetFullContent.length() < originContent.length() * 0.3) {
            return true;
        }
        
        // 2. 关键token检查
        Set<String> originTokens = extractKeyTokens(originContent);
        if (originTokens.isEmpty()) {
            return false;
        }
        
        Set<String> targetTokens = extractKeyTokens(targetFullContent);
        for (String token : originTokens) {
            if (!targetTokens.contains(token)) {
                return true; // 缺少关键token，快速排除
            }
        }
        
        return false;
    }
    
    /**
     * 精确匹配
     */
    private MatchResult findExactMatch(String originContent, String targetFullContent) {
        int position = targetFullContent.indexOf(originContent);
        if (position >= 0) {
            // 找到精确匹配，计算行号
            int startLine = calculateLineNumber(targetFullContent, position) + 1;
            int endLine = calculateLineNumber(targetFullContent, position + originContent.length()) + 1;
            
            return MatchResult.builder()
                .content(originContent)
                .startLine(startLine)
                .endLine(endLine)
                .charPosition(position)
                .confidence(1.0)
                .matchType(MatchType.EXACT)
                .build();
        }
        
        return MatchResult.builder()
            .matchType(MatchType.NONE)
            .confidence(0.0)
            .build();
    }
    
    /**
     * 模糊匹配
     */
    private MatchResult findFuzzyMatch(String originContent, String targetFullContent) {
        if (parallelSearch && targetFullContent.length() > indexThreshold) {
            return parallelSearch(originContent, targetFullContent);
        } else {
            return sequentialSearch(originContent, targetFullContent);
        }
    }
    
    /**
     * 顺序搜索（滑动窗口）
     */
    private MatchResult sequentialSearch(String originContent, String targetFullContent) {
        List<String> originLines = Arrays.asList(originContent.split("\n"));
        List<String> targetLines = Arrays.asList(targetFullContent.split("\n"));
        
        double bestSimilarity = 0.0;
        int bestStartLine = 1;
        int bestEndLine = 1;
        String bestContent = "";
        
        // 滑动窗口搜索
        int windowSize = Math.max(originLines.size(), Math.min(originLines.size() + 10, 50));
        
        for (int startLine = 0; startLine <= targetLines.size() - windowSize; startLine++) {
            int endLine = startLine + windowSize;
            String windowContent = String.join("\n", targetLines.subList(startLine, endLine));
            
            double similarity = calculateBaseSimilarity(originContent, windowContent);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestStartLine = startLine + 1; // 转换为1基行号
                bestEndLine = endLine;
                bestContent = windowContent;
            }
        }
        
        if (bestSimilarity > 0.5) { // 最低相似度阈值
            return MatchResult.builder()
                .content(bestContent)
                .startLine(bestStartLine)
                .endLine(bestEndLine)
                .confidence(bestSimilarity)
                .matchType(MatchType.FUZZY)
                .build();
        }
        
        return MatchResult.builder()
            .matchType(MatchType.NONE)
            .confidence(0.0)
            .build();
    }
    
    /**
     * 并行搜索
     */
    private MatchResult parallelSearch(String originContent, String targetFullContent) {
        // 简化实现：使用单线程搜索
        // 在实际生产环境中可以真正实现并行
        return sequentialSearch(originContent, targetFullContent);
    }
    
    /**
     * 上下文扩展匹配
     */
    private MatchResult findContextMatch(String originContent, String targetFullContent, MatchResult fuzzyMatch) {
        if (!fuzzyMatch.hasMatch()) {
            // 如果模糊匹配都没有，尝试简单的上下文扩展
            return expandContextWithStrategy(originContent, targetFullContent);
        }
        
        // 基于模糊匹配结果进行上下文扩展
        String expandedContent = expandContextAroundMatch(
            fuzzyMatch.getContent(), 
            targetFullContent, 
            fuzzyMatch.getStartLine()
        );
        
        double expandedSimilarity = calculateBaseSimilarity(originContent, expandedContent);
        
        if (expandedSimilarity > fuzzyMatch.getConfidence()) {
            int expandedLines = expandedContent.split("\n").length;
            return MatchResult.builder()
                .content(expandedContent)
                .startLine(Math.max(1, fuzzyMatch.getStartLine() - 5))
                .endLine(fuzzyMatch.getEndLine() + 5)
                .confidence(expandedSimilarity)
                .matchType(MatchType.CONTEXT)
                .expandedLines(expandedLines - fuzzyMatch.getLineCount())
                .build();
        }
        
        return fuzzyMatch;
    }
    
    /**
     * 扩展上下文策略
     */
    private MatchResult expandContextWithStrategy(String originContent, String targetFullContent) {
        List<String> targetLines = Arrays.asList(targetFullContent.split("\n"));
        
        // 简单策略：查找包含origin内容关键字的部分
        Set<String> originTokens = extractKeyTokens(originContent);
        
        for (int i = 0; i < targetLines.size(); i++) {
            String line = targetLines.get(i);
            for (String token : originTokens) {
                if (line.contains(token)) {
                    // 找到包含关键token的行，扩展上下文
                    int startLine = Math.max(0, i - 10);
                    int endLine = Math.min(targetLines.size(), i + 20);
                    String contextContent = String.join("\n", targetLines.subList(startLine, endLine));
                    
                    double similarity = calculateBaseSimilarity(originContent, contextContent);
                    if (similarity > 0.3) {
                        return MatchResult.builder()
                            .content(contextContent)
                            .startLine(startLine + 1)
                            .endLine(endLine)
                            .confidence(similarity)
                            .matchType(MatchType.CONTEXT)
                            .expandedLines(endLine - startLine - 1)
                            .build();
                    }
                }
            }
        }
        
        return MatchResult.builder()
            .matchType(MatchType.NONE)
            .confidence(0.0)
            .build();
    }
    
    /**
     * 在匹配位置周围扩展上下文
     */
    private String expandContextAroundMatch(String matchContent, String targetFullContent, int matchStartLine) {
        List<String> targetLines = Arrays.asList(targetFullContent.split("\n"));
        int startLine = Math.max(0, matchStartLine - 1 - sampleRadius);
        int endLine = Math.min(targetLines.size(), matchStartLine - 1 + sampleRadius);
        
        return String.join("\n", targetLines.subList(startLine, endLine));
    }
    
    /**
     * 计算覆盖相似度
     */
    private double calculateCoverageSimilarity(String originContent, String matchedContent) {
        if (matchedContent == null || matchedContent.trim().isEmpty()) {
            return 0.0;
        }
        
        // 使用编辑距离相似度算法
        return calculateBaseSimilarity(originContent, matchedContent);
    }
    
    /**
     * 提取关键token
     */
    private Set<String> extractKeyTokens(String content) {
        Set<String> tokens = new HashSet<>();
        String[] words = content.replaceAll("[^a-zA-Z0-9_]", " ").split("\\s+");
        
        for (String word : words) {
            if (word.length() > 3) { // 只保留长度大于3的token
                tokens.add(word.toLowerCase());
            }
        }
        
        return tokens;
    }
    
    /**
     * 计算字符位置对应的行号
     */
    private int calculateLineNumber(String content, int charPosition) {
        int lineCount = 0;
        int currentPos = 0;
        
        for (String line : content.split("\n")) {
            if (currentPos + line.length() >= charPosition) {
                return lineCount;
            }
            currentPos += line.length() + 1; // +1 for newline
            lineCount++;
        }
        
        return lineCount;
    }

    
    // ========== 辅助方法 ==========
    
    private String combineBlockContent(DiffBlock block) {
        StringBuilder content = new StringBuilder();
        if (block.getContentFrom() != null && !block.getContentFrom().trim().isEmpty()) {
            content.append(block.getContentFrom());
        }
        if (block.getContentTo() != null && !block.getContentTo().trim().isEmpty()) {
            if (content.length() > 0) {
                content.append("\n");
            }
            content.append(block.getContentTo());
        }
        return content.toString();
    }
    
    private String sampleAroundTargetBlock(String fullContent, DiffBlock targetBlock) {
        // 在目标块周围采样
        String targetContent = combineBlockContent(targetBlock);
        List<String> lines = Arrays.asList(fullContent.split("\n"));
        
        int targetStartLine = targetBlock.getStartLineFrom();
        int targetEndLine = targetBlock.getEndLineFrom();
        
        int startLine = Math.max(1, targetStartLine - sampleRadius);
        int endLine = Math.min(lines.size(), targetEndLine + sampleRadius);
        
        return String.join("\n", lines.subList(startLine - 1, endLine));
    }
    
    private double calculateBaseSimilarity(String content1, String content2) {
        // 使用现有的相似度计算方法（这里简化实现）
        if (content1 == null || content2 == null) return 0.0;
        if (content1.equals(content2)) return 1.0;
        
        // 简单的字符相似度计算
        int maxLength = Math.max(content1.length(), content2.length());
        if (maxLength == 0) return 1.0;
        
        int commonChars = 0;
        for (int i = 0; i < Math.min(content1.length(), content2.length()); i++) {
            if (content1.charAt(i) == content2.charAt(i)) {
                commonChars++;
            }
        }
        
        return (double) commonChars / maxLength;
    }
    
    private String filterCodeNoise(String content) {
        if (content == null) return "";
        
        String[] lines = content.split("\n");
        StringBuilder filtered = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            // 过滤import语句和注释
            if (!trimmed.startsWith("import ") 
                    && !trimmed.startsWith("package ")
                    && !trimmed.startsWith("//")
                    && !trimmed.startsWith("/*")
                    && !trimmed.startsWith("*")
                    && !trimmed.startsWith("*/")) {
                if (filtered.length() > 0) {
                    filtered.append("\n");
                }
                filtered.append(line);
            }
        }
        
        return filtered.toString();
    }
    
    private int countPatternOccurrences(String content, String pattern) {
        if (content == null || pattern == null) return 0;
        return content.split(pattern, -1).length - 1;
    }
    
    private int countCharOccurrences(String content, char target) {
        if (content == null) return 0;
        int count = 0;
        for (char c : content.toCharArray()) {
            if (c == target) count++;
        }
        return count;
    }
    
    private double calculateSimilarity(int value1, int value2) {
        if (value1 == value2) return 1.0;
        int max = Math.max(value1, value2);
        if (max == 0) return 1.0;
        return 1.0 - (double) Math.abs(value1 - value2) / max;
    }
    
    private Set<String> extractVariableNames(String content) {
        Set<String> variables = new HashSet<>();
        // 简单实现：提取以小写字母开头的标识符
        String[] words = content.replaceAll("[^a-zA-Z_]", " ").split("\\s+");
        for (String word : words) {
            if (word.length() > 1 && Character.isLowerCase(word.charAt(0))) {
                variables.add(word);
            }
        }
        return variables;
    }
    
    private Set<String> extractKeywords(String content) {
        Set<String> keywords = new HashSet<>();
        // 提取常见的关键字
        String[] commonKeywords = {"if", "else", "for", "while", "try", "catch", "return", "void", "int", "String", "boolean"};
        String[] words = content.toLowerCase().split("[^a-zA-Z]+");
        for (String word : words) {
            if (Arrays.asList(commonKeywords).contains(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }
    
    /**
     * 发现模式：在完整文件中搜索未匹配的origin内容
     */
    private OptimizationResult discoverMatch(OptimizationContext context, long startTime) {
        try {
            // 获取origin块内容
            String originContent = combineBlockContent(context.getOriginBlock());
            
            // 检查内容长度是否满足最小要求
            if (originContent.length() < discoveryMinContentLength) {
                return OptimizationResult.notOptimized(
                    context.getCurrentSimilarity(),
                    "内容长度不足，跳过发现模式"
                );
            }
            
            // 获取Git信息
            Map<String, Object> additionalData = context.getAdditionalData();
            String targetCommitHash = (String) additionalData.get("targetCommitHash");
            String targetRepoPath = (String) additionalData.get("targetRepoPath");
            String targetFilePath = context.getOriginFile().getRelativePath();
            
            log.debug("开始发现模式搜索: {} in {} @ {}", 
                     originContent.substring(0, Math.min(50, originContent.length())) + "...", 
                     targetFilePath, targetCommitHash);
            
            // 获取完整文件内容
            String targetFileContent = getCompleteFileContent(targetFilePath, targetCommitHash, targetRepoPath);
            
            if (targetFileContent == null) {
                return OptimizationResult.notOptimized(
                    context.getCurrentSimilarity(),
                    "无法获取目标文件内容"
                );
            }
            
            // 在完整文件中搜索最佳匹配
            MatchResult bestMatch = findBestCoverageMatch(originContent, targetFileContent);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 检查是否超过发现模式的超时时间
            if (executionTime > discoveryTimeoutMs) {
                return OptimizationResult.notOptimized(
                    context.getCurrentSimilarity(),
                    "发现模式超时"
                );
            }
            
            // 验证匹配置信度
            if (bestMatch.getConfidence() >= discoveryConfidenceThreshold && bestMatch.hasMatch()) {
                // 创建新的DiffBlock表示发现的内容
                DiffBlock discoveredBlock = createDiscoveredBlock(bestMatch, targetFilePath);
                
                // 构建发现结果
                Map<String, Object> details = new HashMap<>();
                details.put("originContentLength", originContent.length());
                details.put("matchedContentLength", bestMatch.getContentLength());
                details.put("originFilePath", context.getOriginFile().getRelativePath());
                details.put("targetFilePath", targetFilePath);
                details.put("confidence", bestMatch.getConfidence());
                details.put("matchType", bestMatch.getMatchType().toString());
                details.put("discovered", true); // 标记为发现的匹配
                details.put("discoveredBlock", discoveredBlock); // 包含发现的块信息
                
                return OptimizationResult.createOptimized(
                    context.getCurrentSimilarity(),
                    bestMatch.getConfidence(),
                    "发现模式：在完整文件中找到匹配",
                    details,
                    executionTime
                );
            } else {
                return OptimizationResult.notOptimized(
                    context.getCurrentSimilarity(),
                    String.format("未找到足够置信度的匹配 (置信度: %.2f, 阈值: %.2f)", 
                             bestMatch.getConfidence(), discoveryConfidenceThreshold)
                );
            }
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.warn("发现模式搜索失败: {}", e.getMessage(), e);
            
            return OptimizationResult.notOptimized(
                context.getCurrentSimilarity(),
                "发现模式搜索失败: " + e.getMessage()
            );
        }
    }
    
    /**
     * 创建发现的DiffBlock
     */
    private DiffBlock createDiscoveredBlock(MatchResult matchResult, String filePath) {
        DiffBlock discoveredBlock = new DiffBlock();
        discoveredBlock.setContentFrom(matchResult.getContent());
        discoveredBlock.setContentTo(matchResult.getContent());
        discoveredBlock.setStartLineFrom(matchResult.getStartLine());
        discoveredBlock.setEndLineFrom(matchResult.getEndLine());
        discoveredBlock.setStartLineTo(matchResult.getStartLine());
        discoveredBlock.setEndLineTo(matchResult.getEndLine());
        // 注意：DiffBlock没有filePath字段，路径信息保存在details中
        
        return discoveredBlock;
    }
    
    /**
     * 代码段数据类
     */
    private static class CodeSegment {
        private final int startLine;
        private final int endLine;
        private final String content;
        
        public CodeSegment(int startLine, int endLine, String content) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.content = content;
        }
        
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public String getContent() { return content; }
    }
}
