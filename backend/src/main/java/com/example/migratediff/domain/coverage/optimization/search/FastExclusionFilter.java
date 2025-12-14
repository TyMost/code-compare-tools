package com.example.migratediff.domain.coverage.optimization.search;

import com.example.migratediff.domain.coverage.optimization.utils.FeatureExtractor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 快速排除过滤器
 * 基于代码特征快速排除不可能匹配的区域
 */
@Slf4j
public class FastExclusionFilter {
    
    private final FileIndex fileIndex;
    private final double candidateRegionsPercentage;
    
    public FastExclusionFilter(FileIndex fileIndex) {
        this(fileIndex, 20.0); // 默认保留20%的区域
    }
    
    public FastExclusionFilter(FileIndex fileIndex, double candidateRegionsPercentage) {
        this.fileIndex = fileIndex;
        this.candidateRegionsPercentage = Math.min(100.0, Math.max(1.0, candidateRegionsPercentage));
    }
    
    /**
     * 过滤候选区域
     * @param queryFeatures 查询特征
     * @return 候选区域列表（行号）
     */
    public List<Integer> filter(FeatureExtractor.QueryFeatures queryFeatures) {
        if (queryFeatures == null || queryFeatures.isEmpty()) {
            return getAllRegions();
        }
        
        log.debug("开始快速排除过滤，查询特征: tokens={}, methods={}, constants={}", 
                 queryFeatures.getTokens().size(), 
                 queryFeatures.getMethodNames().size(),
                 queryFeatures.getConstants().size());
        
        Set<Integer> candidateRegions = new HashSet<>();
        
        // 策略1：基于方法签名匹配（优先级最高）
        candidateRegions.addAll(filterByMethodSignatures(queryFeatures.getMethodNames()));
        
        // 策略2：基于类签名匹配
        if (candidateRegions.isEmpty()) {
            candidateRegions.addAll(filterByClassSignatures(queryFeatures.getMethodNames()));
        }
        
        // 策略3：基于独特标识符匹配
        if (candidateRegions.isEmpty()) {
            candidateRegions.addAll(filterByUniqueIdentifiers(queryFeatures.getConstants()));
        }
        
        // 策略4：基于Token布隆过滤器
        if (candidateRegions.isEmpty()) {
            candidateRegions.addAll(filterByTokens(queryFeatures.getTokens()));
        }
        
        // 策略5：基于语义块内容匹配
        if (candidateRegions.isEmpty()) {
            candidateRegions.addAll(filterBySemanticBlocks(queryFeatures));
        }
        
        // 如果所有策略都没有结果，返回默认区域
        if (candidateRegions.isEmpty()) {
            candidateRegions.addAll(getDefaultRegions());
        }
        
        // 限制候选区域数量，确保性能
        List<Integer> result = limitCandidateRegions(candidateRegions);
        
        log.debug("快速排除完成，候选区域数: {}", result.size());
        return result;
    }
    
    /**
     * 基于方法签名过滤
     */
    private Set<Integer> filterByMethodSignatures(Set<String> methodNames) {
        Set<Integer> regions = new HashSet<>();
        
        for (String methodName : methodNames) {
            List<Integer> positions = fileIndex.getMethodPositions(methodName);
            regions.addAll(positions);
            
            // 扩展到方法周围区域（前后5行）
            for (Integer position : positions) {
                int start = Math.max(1, position - 5);
                int end = Math.min(fileIndex.getTotalLines(), position + 5);
                for (int line = start; line <= end; line++) {
                    regions.add(line);
                }
            }
        }
        
        log.debug("方法签名过滤找到{}个候选区域", regions.size());
        return regions;
    }
    
    /**
     * 基于类签名过滤
     */
    private Set<Integer> filterByClassSignatures(Set<String> classNames) {
        Set<Integer> regions = new HashSet<>();
        
        for (String className : classNames) {
            List<Integer> positions = fileIndex.getClassPositions(className);
            regions.addAll(positions);
            
            // 扩展到类定义周围区域
            for (Integer position : positions) {
                int start = Math.max(1, position - 10);
                int end = Math.min(fileIndex.getTotalLines(), position + 50);
                for (int line = start; line <= end; line++) {
                    regions.add(line);
                }
            }
        }
        
        log.debug("类签名过滤找到{}个候选区域", regions.size());
        return regions;
    }
    
    /**
     * 基于独特标识符过滤
     */
    private Set<Integer> filterByUniqueIdentifiers(Set<String> identifiers) {
        Set<Integer> regions = new HashSet<>();
        
        // 扫描所有语义块，寻找包含指定标识符的块
        for (FeatureExtractor.CodeBlock block : fileIndex.getSemanticBlocks()) {
            Set<String> blockTokens = com.example.migratediff.domain.coverage.optimization.utils.CodeTokenizer.tokenize(block.getContent());
            
            // 计算匹配的标识符数量
            int matchCount = 0;
            for (String identifier : identifiers) {
                if (blockTokens.contains(identifier)) {
                    matchCount++;
                }
            }
            
            // 如果匹配度超过阈值，添加整个块的区域
            if (matchCount > 0 && (double) matchCount / identifiers.size() >= 0.3) {
                for (int line = block.getStartLine(); line <= block.getEndLine(); line++) {
                    regions.add(line);
                }
            }
        }
        
        log.debug("独特标识符过滤找到{}个候选区域", regions.size());
        return regions;
    }
    
    /**
     * 基于Token布隆过滤器过滤
     */
    private Set<Integer> filterByTokens(Set<String> tokens) {
        Set<Integer> regions = new HashSet<>();
        
        if (!fileIndex.mightContainAllTokens(tokens)) {
            log.debug("布隆过滤器显示文件不包含所有Token");
            return regions;
        }
        
        // 扫描语义块，使用Token相似度筛选
        for (FeatureExtractor.CodeBlock block : fileIndex.getSemanticBlocks()) {
            Set<String> blockTokens = com.example.migratediff.domain.coverage.optimization.utils.CodeTokenizer.tokenize(block.getContent());
            
            // 计算Token覆盖率
            int matchedTokens = 0;
            for (String token : tokens) {
                if (blockTokens.contains(token)) {
                    matchedTokens++;
                }
            }
            
            double coverage = (double) matchedTokens / tokens.size();
            if (coverage >= 0.2) { // 至少20%的Token匹配
                for (int line = block.getStartLine(); line <= block.getEndLine(); line++) {
                    regions.add(line);
                }
            }
        }
        
        log.debug("Token过滤找到{}个候选区域", regions.size());
        return regions;
    }
    
    /**
     * 基于语义块内容过滤
     */
    private Set<Integer> filterBySemanticBlocks(FeatureExtractor.QueryFeatures queryFeatures) {
        Set<Integer> regions = new HashSet<>();
        
        // 提取查询的语义特征
        String queryString = combineQueryFeatures(queryFeatures);
        
        // 计算每个语义块与查询的相似度
        List<BlockSimilarity> blockSimilarities = new ArrayList<>();
        
        for (FeatureExtractor.CodeBlock block : fileIndex.getSemanticBlocks()) {
            double similarity = calculateBlockSimilarity(queryString, block.getContent());
            if (similarity > 0.1) { // 相似度阈值
                blockSimilarities.add(new BlockSimilarity(block, similarity));
            }
        }
        
        // 按相似度排序，取前N个
        blockSimilarities.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        
        int maxBlocks = Math.min(10, blockSimilarities.size()); // 最多取10个块
        for (int i = 0; i < maxBlocks; i++) {
            FeatureExtractor.CodeBlock block = blockSimilarities.get(i).getBlock();
            for (int line = block.getStartLine(); line <= block.getEndLine(); line++) {
                regions.add(line);
            }
        }
        
        log.debug("语义块过滤找到{}个候选区域", regions.size());
        return regions;
    }
    
    /**
     * 合并查询特征为字符串
     */
    private String combineQueryFeatures(FeatureExtractor.QueryFeatures features) {
        StringBuilder combined = new StringBuilder();
        
        if (features.getTokens() != null) {
            combined.append(String.join(" ", features.getTokens()));
        }
        if (features.getMethodNames() != null) {
            combined.append(" ").append(String.join(" ", features.getMethodNames()));
        }
        if (features.getConstants() != null) {
            combined.append(" ").append(String.join(" ", features.getConstants()));
        }
        
        return combined.toString().trim();
    }
    
    /**
     * 计算块相似度
     */
    private double calculateBlockSimilarity(String query, String blockContent) {
        return com.example.migratediff.domain.coverage.optimization.utils.CodeTokenizer.jaccardSimilarity(query, blockContent);
    }
    
    /**
     * 获取所有区域（降级方案）
     */
    private List<Integer> getAllRegions() {
        List<Integer> regions = new ArrayList<>();
        int step = Math.max(1, fileIndex.getTotalLines() / 100); // 最多100个区域
        
        for (int line = 1; line <= fileIndex.getTotalLines(); line += step) {
            regions.add(line);
        }
        
        return regions;
    }
    
    /**
     * 获取默认候选区域
     */
    private Set<Integer> getDefaultRegions() {
        Set<Integer> regions = new HashSet<>();
        
        // 选择一些重要的行号作为候选
        int totalLines = fileIndex.getTotalLines();
        
        // 文件开头
        regions.add(1);
        regions.add(Math.min(10, totalLines));
        
        // 文件中间
        regions.add(totalLines / 2);
        regions.add(totalLines / 2 - 5);
        regions.add(totalLines / 2 + 5);
        
        // 文件末尾
        regions.add(totalLines - 10);
        regions.add(totalLines);
        
        return regions;
    }
    
    /**
     * 限制候选区域数量
     */
    private List<Integer> limitCandidateRegions(Set<Integer> candidateRegions) {
        if (candidateRegions.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 计算目标区域数量
        int totalLines = fileIndex.getTotalLines();
        int targetRegions = Math.max(1, (int) (totalLines * candidateRegionsPercentage / 100.0));
        
        // 如果候选区域已经很少，直接返回
        if (candidateRegions.size() <= targetRegions) {
            List<Integer> result = new ArrayList<>(candidateRegions);
            Collections.sort(result);
            return result;
        }
        
        // 按行号排序，均匀采样
        List<Integer> sortedRegions = new ArrayList<>(candidateRegions);
        Collections.sort(sortedRegions);
        
        List<Integer> result = new ArrayList<>();
        int step = sortedRegions.size() / targetRegions;
        
        for (int i = 0; i < sortedRegions.size() && result.size() < targetRegions; i += step) {
            result.add(sortedRegions.get(i));
        }
        
        return result;
    }
    
    /**
     * 块相似度数据类
     */
    private static class BlockSimilarity {
        private final FeatureExtractor.CodeBlock block;
        private final double similarity;
        
        public BlockSimilarity(FeatureExtractor.CodeBlock block, double similarity) {
            this.block = block;
            this.similarity = similarity;
        }
        
        public FeatureExtractor.CodeBlock getBlock() { return block; }
        public double getSimilarity() { return similarity; }
    }
}
