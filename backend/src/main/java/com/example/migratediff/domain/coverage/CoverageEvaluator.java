package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CoverageEvaluator {

    /** 判断两个变更块是否匹配的展示阈值。 */
    private static final double DISPLAY_SIMILARITY_THRESHOLD = 0.85D;

    /** 噪音过滤开关，用于控制是否过滤import语句和注释 */
    @Value("${coverage.filter.noise.enabled:false}")
    private boolean enableNoiseFiltering;

    /** 是否跳过unmatched的纯噪音块（import-only、comment-only） */
    @Value("${coverage.skip.unmatched.noise.blocks:false}")
    private boolean skipUnmatchedNoiseBlocks;

    /** 块映射器，用于建立ΔO和ΔG之间的一一对应关系 */
    @Autowired
    private OrderAwareBlockMapper blockMapper;

    /**
     * 计算单个文件的覆盖率，使用无阈值算法。
     * 所有块都根据相似度贡献覆盖率，不再使用硬阈值划分。
     */
    public CoverageDetail evaluateFile(DiffFile originFile, DiffFile targetFile) {
        List<DiffBlock> originBlocks = safeBlocks(originFile);
        List<DiffBlock> targetBlocks = safeBlocks(targetFile);

        int totalLines = 0;
        double matchedWeightedLines = 0D;
        List<DiffBlock> matchedBlocks = new ArrayList<>();

        for (DiffBlock originBlock : originBlocks) {
            int lineCount = estimateLineCount(originBlock);
            totalLines += lineCount;

            double similarity = findBestSimilarityForCoverage(originBlock, targetBlocks);
            matchedWeightedLines += similarity * lineCount;
            
            // 方案2：所有块都加入matchedBlocks，移除阈值判断
            matchedBlocks.add(originBlock);
        }

        // 防止除以零：若 ΔO 无有效行，则视为完全覆盖。
        double coverage = totalLines == 0 ? 1D : matchedWeightedLines / (double) totalLines;
        return CoverageDetail.builder()
                .filePath(resolveFilePath(originFile, targetFile))
                .coverage(coverage)
                .matchedLines(matchedWeightedLines)
                .totalLines(totalLines)
                .matchedBlocks(matchedBlocks)
                .unmatchedBlocks(Collections.emptyList()) // 无阈值版本，未匹配列表为空
                .build();
    }

    /**
     * 针对单个文件执行覆盖率计算（保留阈值参数以向后兼容）。
     *
     * @param originFile ΔO 的差异文件，提供基准块
     * @param targetFile ΔG 的差异文件，可能包含匹配块
     * @param threshold  用于划分匹配/未匹配列表的展示阈值（仅用于展示层）
     * @return 含匹配统计与块列表的覆盖率明细
     */
    public CoverageDetail evaluateFile(DiffFile originFile, DiffFile targetFile, double threshold) {
        // 先用无阈值算法计算覆盖率
        CoverageDetail baseDetail = evaluateFile(originFile, targetFile);
        
        // 然后根据阈值重新划分块列表（仅用于展示）
        List<DiffBlock> highSimilarityBlocks = new ArrayList<>();
        List<DiffBlock> lowSimilarityBlocks = new ArrayList<>();
        
        // 为每个块重新计算相似度以进行划分
        List<DiffBlock> targetBlocks = safeBlocks(targetFile);
        for (DiffBlock originBlock : baseDetail.getMatchedBlocks()) {
            double similarity = findBestSimilarityForCoverage(originBlock, targetBlocks);
            if (similarity >= threshold) {
                highSimilarityBlocks.add(originBlock);
            } else {
                lowSimilarityBlocks.add(originBlock);
            }
        }
        
        // 返回相同覆盖率但重新划分的块列表
        return CoverageDetail.builder()
                .filePath(baseDetail.getFilePath())
                .coverage(baseDetail.getCoverage())
                .matchedLines(baseDetail.getMatchedLines())
                .totalLines(baseDetail.getTotalLines())
                .matchedBlocks(highSimilarityBlocks)  // 高相似度块
                .unmatchedBlocks(lowSimilarityBlocks) // 低相似度块
                .build();
    }

    /**
     * 基于一一映射的文件覆盖率计算（新方法）
     * 使用OrderAwareBlockMapper建立明确的一一对应关系，然后计算覆盖率
     * 采用无阈值算法，所有块都根据相似度贡献覆盖率
     *
     * @param originFile ΔO 的差异文件，提供基准块
     * @param targetFile ΔG 的差异文件，可能包含匹配块
     * @param threshold  用于划分匹配/未匹配列表的展示阈值（仅用于展示层）
     * @return 含匹配统计与块列表的覆盖率明细
     */
    public CoverageDetail evaluateFileWithMapping(DiffFile originFile, DiffFile targetFile, double threshold) {
        List<DiffBlock> originBlocks = safeBlocks(originFile);
        List<DiffBlock> targetBlocks = safeBlocks(targetFile);

        // 使用新的块映射算法建立一一对应关系
        BlockMapping mapping = blockMapper.createMapping(originBlocks, targetBlocks);

        // 计算总行数和匹配行数
        int totalLines = 0;
        double matchedWeightedLines = 0D;
        List<DiffBlock> allBlocks = new ArrayList<>();

        // 处理已匹配的块：使用无噪音的相似度计算覆盖率
        for (DiffBlock originBlock : mapping.getMatchedOracleBlocks()) {
            // 关键修复：使用去噪后的行数作为权重
            int noiseFreeLineCount = calculateNoiseFreeLineCount(originBlock);
            totalLines += noiseFreeLineCount;

            // 使用无噪音的相似度进行覆盖率计算
            DiffBlock targetBlock = mapping.getGaussBlock(originBlock);
            double similarity = calculateSimilarityWithoutNoiseTokens(
                getOrDualTokenize(originBlock),
                getOrDualTokenize(targetBlock)
            );
            // 相似度 × 去噪行数
            matchedWeightedLines += similarity * noiseFreeLineCount;
            allBlocks.add(originBlock);  // 直接加入，无需阈值判断
        }

        // 处理未匹配的块：智能跳过纯噪音块
        for (DiffBlock originBlock : mapping.getUnmatchedOracle()) {
            // 检查是否为纯噪音块，如果是且启用了跳过功能，则不计入总数
            if (skipUnmatchedNoiseBlocks && isPureNoiseBlock(originBlock)) {
                // 跳过纯噪音块，不计入总数和块列表
                continue;
            }
            
            int lineCount = estimateLineCount(originBlock);
            totalLines += lineCount;
            allBlocks.add(originBlock);  // 加入allBlocks，但贡献为0
        }

        // 防止除以零：若 ΔO 无有效行，则视为完全覆盖。
        double coverage = totalLines == 0 ? 1D : matchedWeightedLines / (double) totalLines;
        
        // 根据阈值重新划分块列表（仅用于展示）
        List<DiffBlock> highSimilarityBlocks = new ArrayList<>();
        List<DiffBlock> lowSimilarityBlocks = new ArrayList<>();
        
        for (DiffBlock originBlock : mapping.getMatchedOracleBlocks()) {
            double similarity = mapping.getSimilarity(originBlock);
            if (similarity >= threshold) {
                highSimilarityBlocks.add(originBlock);
            } else {
                lowSimilarityBlocks.add(originBlock);
            }
        }
        
        // 未匹配的块都加入低相似度列表
        lowSimilarityBlocks.addAll(mapping.getUnmatchedOracle());
        
        return CoverageDetail.builder()
                .filePath(resolveFilePath(originFile, targetFile))
                .coverage(coverage)
                .matchedLines(matchedWeightedLines)
                .totalLines(totalLines)
                .matchedBlocks(highSimilarityBlocks)  // 高相似度块
                .unmatchedBlocks(lowSimilarityBlocks) // 低相似度块
                .build();
    }

    /**
     * 获取文件的块映射关系（为迁移功能提供接口）
     *
     * @param originFile ΔO 的差异文件
     * @param targetFile ΔG 的差异文件
     * @return 块映射结果
     */
    public BlockMapping getBlockMapping(DiffFile originFile, DiffFile targetFile) {
        List<DiffBlock> originBlocks = safeBlocks(originFile);
        List<DiffBlock> targetBlocks = safeBlocks(targetFile);
        return blockMapper.createMapping(originBlocks, targetBlocks);
    }

    /**
     * 计算文件列表的覆盖率明细，使用默认阈值。
     */
    public CoverageSummary evaluateFiles(List<DiffFile> deltaOFiles, List<DiffFile> deltaGFiles) {
        return evaluateFiles(deltaOFiles, deltaGFiles, DISPLAY_SIMILARITY_THRESHOLD);
    }

    /**
     * 计算文件列表的覆盖率，并聚合总计信息。
     *
     * @param deltaOFiles ΔO 的差异文件列表
     * @param deltaGFiles ΔG 的差异文件列表
     * @param threshold   展示层用于划分匹配块的最小相似度
     * @return 汇总后的覆盖率结果
     */
    public CoverageSummary evaluateFiles(List<DiffFile> deltaOFiles,
                                         List<DiffFile> deltaGFiles,
                                         double threshold) {
        List<DiffFile> safeDeltaO = deltaOFiles == null ? Collections.emptyList() : deltaOFiles;
        Map<String, DiffFile> targetIndex = indexByPath(deltaGFiles);

        List<CoverageDetail> details = new ArrayList<>();
        double totalMatchedLines = 0D;
        int totalLines = 0;

        for (DiffFile originFile : safeDeltaO) {
            if (originFile == null) {
                continue;
            }
            DiffFile candidate = targetIndex.getOrDefault(originFile.getRelativePath(), null);
            CoverageDetail detail = evaluateFile(originFile, candidate, threshold);
            details.add(detail);
            totalMatchedLines += detail.getMatchedLines();
            totalLines += detail.getTotalLines();
        }

        double overallCoverage = totalLines == 0 ? 1D : (double) totalMatchedLines / (double) totalLines;
        CoverageSummary summary = CoverageSummary.builder()
                .overallCoverage(overallCoverage)
                .totalMatchedLines(totalMatchedLines)
                .totalLines(totalLines)
                .build();
        summary.getDetails().addAll(details);
        return summary;
    }

    private Map<String, DiffFile> indexByPath(List<DiffFile> files) {
        Map<String, DiffFile> index = new HashMap<>();
        if (files == null) {
            return index;
        }
        for (DiffFile file : files) {
            if (file == null) {
                continue;
            }
            String relativePath = file.getRelativePath();
            if (relativePath != null) {
                index.put(relativePath, file);
            }
        }
        return index;
    }

    private List<DiffBlock> safeBlocks(DiffFile file) {
        if (file == null || file.getBlocks() == null) {
            return Collections.emptyList();
        }
        List<DiffBlock> filtered = new ArrayList<>();
        for (DiffBlock block : file.getBlocks()) {
            if (block != null) {
                filtered.add(block);
            }
        }
        return filtered;
    }

    /**
     * 为覆盖率计算寻找最佳相似度（启用噪音过滤）
     */
    private double findBestSimilarityForCoverage(DiffBlock originBlock, List<DiffBlock> targetBlocks) {
        double bestSimilarity = 0D;
        for (DiffBlock candidate : targetBlocks) {
            double similarity = blockSimilarityForCoverage(originBlock, candidate);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
            }
        }
        return bestSimilarity;
    }

    /**
     * 块匹配时寻找最佳相似度（不过滤噪音）
     */
    private double findBestSimilarityForBlockMatching(DiffBlock originBlock, List<DiffBlock> targetBlocks) {
        double bestSimilarity = 0D;
        for (DiffBlock candidate : targetBlocks) {
            double similarity = blockSimilarityForBlockMatching(originBlock, candidate);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
            }
        }
        return bestSimilarity;
    }

    /**
     * 覆盖率计算时使用的块相似度（启用噪音过滤）
     */
    private double blockSimilarityForCoverage(DiffBlock origin, DiffBlock target) {
        // --- Normalize content ---
        String oFromStr = normalize(origin.getContentFrom());
        String oToStr   = normalize(origin.getContentTo());
        String tFromStr = normalize(target.getContentFrom());
        String tToStr   = normalize(target.getContentTo());

        // --- Tokenize once (启用噪音过滤) ---
        List<String> oFrom = tokenizeIfNotEmpty(oFromStr);
        List<String> oTo   = tokenizeIfNotEmpty(oToStr);
        List<String> tFrom = tokenizeIfNotEmpty(tFromStr);
        List<String> tTo   = tokenizeIfNotEmpty(tToStr);

        // --- Weighted 4-way similarity ---
        double deleteSimilarity = score(oFrom, tFrom, 1.0);  // 删除同源
        double addSimilarity = score(oTo, tTo, 1.0);    // 添加同源
        double crossSimilarity1 = score(oFrom, tTo, 0.3); // cross
        double crossSimilarity2 = score(oTo, tFrom, 0.3); // cross
        
        // 返回最大值，优先同源匹配
        return Math.max(Math.max(deleteSimilarity, addSimilarity), 
                     Math.max(crossSimilarity1, crossSimilarity2));
    }

    /**
     * 双Tokenization缓存类，支持带噪音和无噪音两种tokenization
     */
    private static class DualTokenizedBlock {
        final List<String> fromTokensWithNoise;    // 包含噪音（匹配用）
        final List<String> toTokensWithNoise;      // 包含噪音（匹配用）
        final List<String> fromTokensWithoutNoise;  // 无噪音（计算用）
        final List<String> toTokensWithoutNoise;    // 无噪音（计算用）
        final boolean isEmpty;
        
        DualTokenizedBlock(DiffBlock block) {
            // 计算完整tokens（匹配用，保留import和注释）
            String fromStr = normalizeString(block.getContentFrom());
            String toStr = normalizeString(block.getContentTo());
            this.fromTokensWithNoise = tokenizeString(fromStr, false); // 不过滤噪音
            this.toTokensWithNoise = tokenizeString(toStr, false);     // 不过滤噪音
            
            // 计算过滤后的tokens（计算用，去除import和注释）
            String fromFiltered = CoverageUtils.filterCodeNoise(fromStr);
            String toFiltered = CoverageUtils.filterCodeNoise(toStr);
            this.fromTokensWithoutNoise = tokenizeString(fromFiltered, false); // 内容已过滤，tokenize时不需要再过滤
            this.toTokensWithoutNoise = tokenizeString(toFiltered, false);     // 内容已过滤，tokenize时不需要再过滤
            
            // 空块判断逻辑保持不变
            this.isEmpty = (fromTokensWithNoise == null && toTokensWithNoise == null) || 
                          (fromTokensWithNoise != null && toTokensWithNoise != null && 
                           fromTokensWithNoise.isEmpty() && toTokensWithNoise.isEmpty());
        }
        
        private String normalizeString(String s) {
            return (s == null) ? "" : s.trim();
        }
        
        private List<String> tokenizeString(String s, boolean filterNoise) {
            if (s == null || s.isEmpty()) return null;
            return CoverageUtils.tokenize(s, filterNoise);
        }
    }

    /** 双Tokenization缓存 */
    private final Map<DiffBlock, DualTokenizedBlock> dualTokenCache = new ConcurrentHashMap<>();

    /**
     * 块匹配时使用的块相似度（不过滤噪音，保留完整信息）
     */
    private double blockSimilarityForBlockMatching(DiffBlock origin, DiffBlock target) {
        DualTokenizedBlock oTokens = getOrDualTokenize(origin);
        DualTokenizedBlock tTokens = getOrDualTokenize(target);
        
        // 使用带噪音的tokens进行匹配
        return calculateSimilarityWithNoiseTokens(oTokens, tTokens);
    }

    /**
     * 使用带噪音的tokens计算相似度
     */
    private double calculateSimilarityWithNoiseTokens(DualTokenizedBlock o, DualTokenizedBlock t) {
        // --- Weighted 4-way similarity ---
        double deleteSimilarity = score(o.fromTokensWithNoise, t.fromTokensWithNoise, 1.0);  // 删除同源
        double addSimilarity = score(o.toTokensWithNoise, t.toTokensWithNoise, 1.0);    // 添加同源
        double crossSimilarity1 = score(o.fromTokensWithNoise, t.toTokensWithNoise, 0.3); // cross
        double crossSimilarity2 = score(o.toTokensWithNoise, t.fromTokensWithNoise, 0.3); // cross
        
        // 返回最大值，优先同源匹配
        return Math.max(Math.max(deleteSimilarity, addSimilarity), 
                     Math.max(crossSimilarity1, crossSimilarity2));
    }

    /**
     * 使用无噪音的tokens计算相似度
     * 修复纯噪音块的处理逻辑
     */
    private double calculateSimilarityWithoutNoiseTokens(DualTokenizedBlock o, DualTokenizedBlock t) {
        // 检查是否为纯噪音
        boolean oIsPureNoise = isEmptyOrEmpty(o.fromTokensWithoutNoise);
        boolean tIsPureNoise = isEmptyOrEmpty(t.fromTokensWithoutNoise);
        
        // 情况1：两边都是纯噪音 - 完全匹配
        if (oIsPureNoise && tIsPureNoise) {
            return 1.0;
        }
        
        // 情况2：只有一边是纯噪音 - 完全不匹配
        if (oIsPureNoise || tIsPureNoise) {
            return 0.0;
        }
        
        // 情况3：两边都有代码 - 正常计算无噪音相似度
        double deleteSimilarity = score(o.fromTokensWithoutNoise, t.fromTokensWithoutNoise, 1.0);  // 删除同源
        double addSimilarity = score(o.toTokensWithoutNoise, t.toTokensWithoutNoise, 1.0);    // 添加同源
        double crossSimilarity1 = score(o.fromTokensWithoutNoise, t.toTokensWithoutNoise, 0.3); // cross
        double crossSimilarity2 = score(o.toTokensWithoutNoise, t.fromTokensWithoutNoise, 0.3); // cross
        
        // 返回最大值，优先同源匹配
        return Math.max(Math.max(deleteSimilarity, addSimilarity), 
                     Math.max(crossSimilarity1, crossSimilarity2));
    }

    /**
     * 辅助方法：检查列表是否为空或null
     */
    private boolean isEmptyOrEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }

    /**
     * 检测是否为纯噪音块（只包含import语句或注释）
     * 
     * @param block 要检测的块
     * @return 如果是纯噪音块返回true，否则返回false
     */
    private boolean isPureNoiseBlock(DiffBlock block) {
        if (block == null) {
            return false;
        }

        // 获取块的内容
        String content = resolveContent(block, true); // 优先使用from内容
        if (content == null || content.trim().isEmpty()) {
            content = resolveContent(block, false); // 如果from为空，使用to内容
        }

        if (content == null || content.trim().isEmpty()) {
            return false; // 空块不算纯噪音块
        }

        // 过滤掉噪音内容，看是否还有剩余内容
        String filteredContent = CoverageUtils.filterCodeNoise(content);
        
        // 如果过滤后为空或只有空白字符，说明是纯噪音块
        return filteredContent == null || filteredContent.trim().isEmpty();
    }

    /**
     * 计算去噪后的行数
     * 用于覆盖率计算的权重，确保相似度和权重基于相同的无噪音内容
     */
    private int calculateNoiseFreeLineCount(DiffBlock block) {
        String fromContent = resolveContent(block, true);
        String toContent = resolveContent(block, false);
        
        // 过滤噪音
        String fromFiltered = CoverageUtils.filterCodeNoise(fromContent);
        String toFiltered = CoverageUtils.filterCodeNoise(toContent);
        
        // 选择非空的过滤后内容
        String contentForLineCount = null;
        if (fromFiltered != null && !fromFiltered.trim().isEmpty()) {
            contentForLineCount = fromFiltered;
        } else if (toFiltered != null && !toFiltered.trim().isEmpty()) {
            contentForLineCount = toFiltered;
        }
        
        // 纯噪音块返回0行
        if (contentForLineCount == null || contentForLineCount.trim().isEmpty()) {
            return 0;
        }
        
        return countLines(contentForLineCount);
    }

    /**
     * 获取或计算块的双Tokenized表示
     */
    private DualTokenizedBlock getOrDualTokenize(DiffBlock block) {
        return dualTokenCache.computeIfAbsent(block, DualTokenizedBlock::new);
    }

    private List<String> tokenizeIfNotEmpty(String s) {
        if (s == null || s.isEmpty()) return null;
        return CoverageUtils.tokenize(s, enableNoiseFiltering);
    }

    /**
     * 块匹配时使用的tokenization（不过滤噪音，保留完整信息）
     */
    private List<String> tokenizeForBlockMatching(String s) {
        if (s == null || s.isEmpty()) return null;
        return CoverageUtils.tokenize(s, false); // 块匹配时不过滤噪音
    }

    private double score(List<String> a, List<String> b, double weight) {
        if (a == null || b == null) return 0;
        return CoverageUtils.recallSimilarity(a, b) * weight;
    }

    private String normalize(String s) {
        return (s == null) ? "" : s.trim();
    }

    private String resolveContent(DiffBlock block, boolean preferSource) {
        if (block == null) {
            return "";
        }
        String content = preferSource ? block.getContentFrom() : block.getContentTo();
        if (content == null || content.trim().isEmpty()) {
            content = preferSource ? block.getContentTo() : block.getContentFrom();
        }
        return content == null ? "" : content;
    }

    private int estimateLineCount(DiffBlock block) {
        if (block == null) {
            return 0;
        }
        String content = block.getContentFrom();
        if (content == null || content.trim().isEmpty()) {
            content = block.getContentTo();
        }
        if (content != null && !content.isEmpty()) {
            return countLines(content);
        }
        int fromLines = safeLineSpan(block.getStartLineFrom(), block.getEndLineFrom());
        if (fromLines > 0) {
            return fromLines;
        }
        return safeLineSpan(block.getStartLineTo(), block.getEndLineTo());
    }

    private int countLines(String content) {
        int lines = 0;
        boolean sawCharacter = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (current == '\n') {
                lines++;
                sawCharacter = false;
            } else if (current != '\r') {
                sawCharacter = true;
            }
        }
        if (sawCharacter) {
            lines++;
        }
        return lines;
    }

    private int safeLineSpan(int start, int end) {
        if (start <= 0 || end < start) {
            return 0;
        }
        return end - start + 1;
    }

    private String resolveFilePath(DiffFile deltaO, DiffFile deltaG) {
        if (deltaO != null && deltaO.getRelativePath() != null) {
            return deltaO.getRelativePath();
        }
        if (deltaG != null) {
            return deltaG.getRelativePath();
        }
        return "";
    }
}
