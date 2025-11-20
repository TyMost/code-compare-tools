package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CoverageEvaluator {

    /** 判断两个变更块是否匹配的展示阈值。 */
    private static final double DISPLAY_SIMILARITY_THRESHOLD = 0.85D;

    /**
     * 计算单个文件的覆盖率，使用默认阈值。
     */
    public CoverageDetail evaluateFile(DiffFile originFile, DiffFile targetFile) {
        return evaluateFile(originFile, targetFile, DISPLAY_SIMILARITY_THRESHOLD);
    }

    /**
     * 针对单个文件执行覆盖率计算。
     *
     * @param originFile ΔO 的差异文件，提供基准块
     * @param targetFile ΔG 的差异文件，可能包含匹配块
     * @param threshold  用于划分匹配/未匹配列表的展示阈值
     * @return 含匹配统计与块列表的覆盖率明细
     */
    public CoverageDetail evaluateFile(DiffFile originFile, DiffFile targetFile, double threshold) {
        List<DiffBlock> originBlocks = safeBlocks(originFile);
        List<DiffBlock> targetBlocks = safeBlocks(targetFile);

        int totalLines = 0;
        double matchedWeightedLines = 0D;
        List<DiffBlock> matchedBlocks = new ArrayList<>();
        List<DiffBlock> unmatchedBlocks = new ArrayList<>();

        for (DiffBlock originBlock : originBlocks) {
            int lineCount = estimateLineCount(originBlock);
            totalLines += lineCount;

            double similarity = findBestSimilarity(originBlock, targetBlocks);
            matchedWeightedLines += similarity * lineCount;
            if (similarity >= threshold) {
                matchedBlocks.add(originBlock);
            } else {
                unmatchedBlocks.add(originBlock);
            }
        }

        // 防止除以零：若 ΔO 无有效行，则视为完全覆盖。
        double coverage = totalLines == 0 ? 1D : matchedWeightedLines / (double) totalLines;
        return CoverageDetail.builder()
                .filePath(resolveFilePath(originFile, targetFile))
                .coverage(coverage)
                .matchedLines(matchedWeightedLines)
                .totalLines(totalLines)
                .matchedBlocks(matchedBlocks)
                .unmatchedBlocks(unmatchedBlocks)
                .build();
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

    private double findBestSimilarity(DiffBlock originBlock, List<DiffBlock> targetBlocks) {
        double bestSimilarity = 0D;
        for (DiffBlock candidate : targetBlocks) {
            double similarity = blockSimilarity(originBlock, candidate);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
            }
        }
        return bestSimilarity;
    }

    private double blockSimilarity(DiffBlock origin, DiffBlock target) {

        // --- Normalize content ---
        String oFromStr = normalize(origin.getContentFrom());
        String oToStr   = normalize(origin.getContentTo());
        String tFromStr = normalize(target.getContentFrom());
        String tToStr   = normalize(target.getContentTo());

        // --- Tokenize once (性能优化的关键部分) ---
        List<String> oFrom = tokenizeIfNotEmpty(oFromStr);
        List<String> oTo   = tokenizeIfNotEmpty(oToStr);
        List<String> tFrom = tokenizeIfNotEmpty(tFromStr);
        List<String> tTo   = tokenizeIfNotEmpty(tToStr);

        // --- Weighted 4-way similarity ---
        return max(
            score(oFrom, tFrom, 1.0),  // 删除同源
            score(oTo,   tTo,   1.0),  // 添加同源
            score(oFrom, tTo,   0.3),  // cross
            score(oTo,   tFrom, 0.3)   // cross
        );
    }

    private List<String> tokenizeIfNotEmpty(String s) {
        if (s == null || s.isEmpty()) return null;
        return CoverageUtils.tokenize(s);
    }

    private double score(List<String> a, List<String> b, double weight) {
        if (a == null || b == null) return 0;
        return CoverageUtils.recallSimilarity(a, b) * weight;
    }

    private String normalize(String s) {
        return (s == null) ? "" : s.trim();
    }

    private double max(double... xs) {
        double m = 0;
        for (double x : xs) if (x > m) m = x;
        return m;
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
