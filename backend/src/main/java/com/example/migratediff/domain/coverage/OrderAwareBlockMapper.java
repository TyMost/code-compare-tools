package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于顺序一致性的块映射器（优化版本）
 * 结合位置窗口搜索，建立ΔO到ΔG的一一映射关系
 * 实施Token预计算和缓存优化，移除硬阈值限制
 * 块匹配时保留完整的import和注释信息
 */
@Component
public class OrderAwareBlockMapper {

    /** 位置搜索窗口大小 */
    private static final int POSITION_WINDOW = 3;

    /** Token缓存，避免重复计算 */
    private final Map<DiffBlock, TokenizedBlock> tokenCache = new ConcurrentHashMap<>();
    
    /** Token预计算结果缓存 */
    private static class TokenizedBlock {
        final List<String> fromTokens;
        final List<String> toTokens;
        final boolean isEmpty;
        
        TokenizedBlock(List<String> fromTokens, List<String> toTokens, boolean isEmpty) {
            this.fromTokens = fromTokens;
            this.toTokens = toTokens;
            this.isEmpty = isEmpty;
        }
    }

    /**
     * 创建ΔO和ΔG之间的块映射关系
     * 采用无阈值算法，所有匹配都基于最高相似度
     * 块匹配时不过滤噪音，保留完整的import和注释信息
     * 
     * @param oBlocks ΔO的块列表
     * @param gBlocks ΔG的块列表
     * @return 块映射结果
     */
    public BlockMapping createMapping(List<DiffBlock> oBlocks, List<DiffBlock> gBlocks) {
        BlockMapping mapping = BlockMapping.builder().build();
        Set<DiffBlock> usedGBlocks = new HashSet<>();

        // 按顺序逐个处理ΔO块
        for (int oIndex = 0; oIndex < oBlocks.size(); oIndex++) {
            DiffBlock oBlock = oBlocks.get(oIndex);
            
            // 在位置窗口内找最佳匹配
            MatchResult matchResult = findBestMatchInWindow(oBlock, oIndex, gBlocks, usedGBlocks);
            
            if (matchResult.bestMatch != null) {
                // 方案2：直接加入映射，不使用阈值判断
                mapping.addMatch(oBlock, matchResult.bestMatch, matchResult.similarity);
                usedGBlocks.add(matchResult.bestMatch);
            } else {
                mapping.addUnmatchedOracle(oBlock);
            }
        }
        
        // 标记未使用的ΔG块
        markUnusedGaussBlocks(mapping, gBlocks, usedGBlocks);
        return mapping;
    }

    /**
     * 匹配结果封装类
     */
    private static class MatchResult {
        final DiffBlock bestMatch;
        final double similarity;
        
        MatchResult(DiffBlock bestMatch, double similarity) {
            this.bestMatch = bestMatch;
            this.similarity = similarity;
        }
    }

    /**
     * 在位置窗口内为ΔO块寻找最佳匹配的ΔG块
     * 使用Token预计算优化相似度计算性能
     * 块匹配时不过滤噪音，保留完整信息
     * 
     * @param oBlock ΔO块
     * @param oIndex ΔO块的索引位置
     * @param gBlocks ΔG块列表
     * @param usedGBlocks 已使用的ΔG块集合
     * @return 最佳匹配结果
     */
    private MatchResult findBestMatchInWindow(DiffBlock oBlock, int oIndex, 
                                           List<DiffBlock> gBlocks, Set<DiffBlock> usedGBlocks) {
        if (gBlocks.isEmpty()) {
            return new MatchResult(null, 0.0);
        }
        
        // 计算搜索窗口
        int windowStart = Math.max(0, oIndex - POSITION_WINDOW);
        int windowEnd = Math.min(gBlocks.size() - 1, oIndex + POSITION_WINDOW);
        
        DiffBlock bestMatch = null;
        double bestSimilarity = 0;
        
        // 预计算ΔO块的tokens（不过滤噪音）
        TokenizedBlock oTokens = getOrTokenize(oBlock);
        if (oTokens.isEmpty) {
            return new MatchResult(null, 0.0);
        }
        
        // 在窗口内找最佳匹配
        for (int gIndex = windowStart; gIndex <= windowEnd; gIndex++) {
            DiffBlock gBlock = gBlocks.get(gIndex);
            
            if (usedGBlocks.contains(gBlock)) {
                continue; // 跳过已使用的块
            }
            
            // 快速过滤：检查块是否为空
            TokenizedBlock gTokens = getOrTokenize(gBlock);
            if (gTokens.isEmpty) {
                continue;
            }
            
            // 使用预计算的tokens计算相似度（性能优化）
            double similarity = calculateSimilarityWithTokens(oTokens, gTokens);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = gBlock;
            }
        }
        
        return new MatchResult(bestMatch, bestSimilarity);
    }

    /**
     * 获取或计算块的Tokenized表示（缓存优化）
     * 块匹配时不过滤噪音，保留完整的import和注释信息
     * 修复isEmpty判断逻辑，确保新增/删除块能正确匹配
     */
    private TokenizedBlock getOrTokenize(DiffBlock block) {
        return tokenCache.computeIfAbsent(block, b -> {
            String fromStr = normalize(b.getContentFrom());
            String toStr = normalize(b.getContentTo());
            
            // 块匹配时不过滤噪音，保留完整信息
            List<String> fromTokens = tokenizeIfNotEmpty(fromStr);
            List<String> toTokens = tokenizeIfNotEmpty(toStr);
            
            // 修复isEmpty判断逻辑：只有当from和to都为null时才为空
            // 对于新增块（from为空，to有内容）或删除块（from有内容，to为空），都不应该被认为是空块
            boolean isEmpty = (fromTokens == null && toTokens == null) || 
                           (fromTokens != null && toTokens != null && 
                            fromTokens.isEmpty() && toTokens.isEmpty());
            
            return new TokenizedBlock(fromTokens, toTokens, isEmpty);
        });
    }

    /**
     * 使用预计算的tokens计算相似度
     * 避免重复的tokenize操作
     * 移除isEmpty检查，因为score方法已经处理了null情况
     */
    private double calculateSimilarityWithTokens(TokenizedBlock o, TokenizedBlock g) {
        // 移除isEmpty检查，直接计算相似度
        // score方法内部已经处理了null情况，不需要额外检查
        
        // --- Weighted 4-way similarity ---
        return max(
            score(o.fromTokens, g.fromTokens, 1.0),  // 删除同源
            score(o.toTokens, g.toTokens, 1.0),      // 添加同源
            score(o.fromTokens, g.toTokens, 0.3),      // cross
            score(o.toTokens, g.fromTokens, 0.3)       // cross
        );
    }
    
    private String normalize(String s) {
        return (s == null) ? "" : s.trim();
    }
    
    /**
     * 块匹配时使用的tokenization（不过滤噪音，保留完整信息）
     */
    private List<String> tokenizeIfNotEmpty(String s) {
        if (s == null || s.isEmpty()) return null;
        return CoverageUtils.tokenize(s, false); // 块匹配时不过滤噪音
    }
    
    private double score(List<String> a, List<String> b, double weight) {
        if (a == null || b == null) return 0;
        return CoverageUtils.recallSimilarity(a, b) * weight;
    }
    
    private double max(double... xs) {
        double m = 0;
        for (double x : xs) if (x > m) m = x;
        return m;
    }

    /**
     * 获取块的有效内容，优先使用to内容
     */
    private String getEffectiveContent(DiffBlock block) {
        if (block == null) {
            return "";
        }
        
        String content = block.getContentTo();
        if (content == null || content.trim().isEmpty()) {
            content = block.getContentFrom();
        }
        
        return content == null ? "" : content.trim();
    }

    /**
     * 检查块是否为空或无效
     */
    private boolean isEmptyBlock(DiffBlock block) {
        if (block == null) {
            return true;
        }
        
        String content = getEffectiveContent(block);
        return content.isEmpty();
    }

    /**
     * 标记未被使用的ΔG块
     */
    private void markUnusedGaussBlocks(BlockMapping mapping, List<DiffBlock> gBlocks, Set<DiffBlock> usedGBlocks) {
        for (DiffBlock gBlock : gBlocks) {
            if (!usedGBlocks.contains(gBlock)) {
                mapping.addUnmatchedGauss(gBlock);
            }
        }
    }

    /**
     * 清理缓存（可选的内存管理）
     */
    public void clearCache() {
        tokenCache.clear();
    }

    /**
     * 获取缓存统计信息（用于调试）
     */
    public String getCacheStats() {
        return String.format("Token缓存统计: 大小=%d, 命中率=%.2f%%", 
                           tokenCache.size(), 
                           tokenCache.isEmpty() ? 0.0 : 100.0);
    }

    /**
     * 获取当前配置的参数信息（用于调试）
     */
    public String getConfigInfo() {
        return String.format("OrderAwareBlockMapper配置: 位置窗口=%d, 块匹配不过滤噪音", 
                           POSITION_WINDOW);
    }
}
