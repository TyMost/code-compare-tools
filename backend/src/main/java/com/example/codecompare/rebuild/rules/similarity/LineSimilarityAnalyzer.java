package com.example.codecompare.rebuild.rules.similarity;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.support.DiffSimilarityCalculator;
import com.github.difflib.diffutils.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 针对单个 {@link BlockDiff} 计算逐行相似度的分析器，提供高精度但仍保持较高性能的结果。
 */
@Component
public class LineSimilarityAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LineSimilarityAnalyzer.class);

    private static final int MAX_ANALYZED_LINE_COUNT = 4000;

    public LineSimilaritySnapshot analyze(BlockDiff diff) {
        if (diff == null) {
            return LineSimilaritySnapshot.empty();
        }
        List<String> sourceLines = sanitize(diff.getSourceLines());
        List<String> targetLines = sanitize(diff.getTargetLines());
        int combinedSize = sourceLines.size() + targetLines.size();
        if (combinedSize == 0) {
            return LineSimilaritySnapshot.empty();
        }
        if (combinedSize > MAX_ANALYZED_LINE_COUNT) {
            log.trace("Diff 太大，使用快速估算模式 sourceLines={}, targetLines={}", sourceLines.size(), targetLines.size());
            return fallbackSnapshot(diff, sourceLines, targetLines);
        }
        return preciseSnapshot(diff, sourceLines, targetLines);
    }

    private LineSimilaritySnapshot preciseSnapshot(BlockDiff diff,
                                                   List<String> sourceLines,
                                                   List<String> targetLines) {
        List<LineComparison> comparisons = new ArrayList<>();
        try {
            Patch<String> patch = DiffUtils.diff(sourceLines, targetLines);
            int srcCursor = 0;
            int tgtCursor = 0;
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                int srcPos = delta.getSource().getPosition();
                int tgtPos = delta.getTarget().getPosition();
                while (srcCursor < srcPos && tgtCursor < tgtPos) {
                    comparisons.add(matchComparison(diff, sourceLines, targetLines, srcCursor, tgtCursor));
                    srcCursor++;
                    tgtCursor++;
                }
                DeltaType type = delta.getType();
                switch (type) {
                    case INSERT:
                        tgtCursor = handleInsert(diff, targetLines, comparisons, tgtCursor, delta.getTarget().getLines());
                        break;
                    case DELETE:
                        srcCursor = handleDelete(diff, sourceLines, comparisons, srcCursor, delta.getSource().getLines());
                        break;
                    case CHANGE:
                        int changeSrcStart = srcCursor;
                        int changeTgtStart = tgtCursor;
                        srcCursor = handleChange(diff, sourceLines, targetLines, comparisons,
                                srcCursor, tgtCursor, delta.getSource().getLines(), delta.getTarget().getLines());
                        tgtCursor += (srcCursor - changeSrcStart) - (delta.getSource().getLines().size() - delta.getTarget().getLines().size());
                        // 上面逻辑难以维护，直接重新赋值
                        tgtCursor = changeTgtStart;
                        int min = Math.min(delta.getSource().getLines().size(), delta.getTarget().getLines().size());
                        tgtCursor += min;
                        if (delta.getTarget().getLines().size() > min) {
                            tgtCursor += delta.getTarget().getLines().size() - min;
                        }
                        break;
                    default:
                        log.debug("未知 DeltaType {}，退回为 CHANGE 处理", type);
                        srcCursor = handleChange(diff, sourceLines, targetLines, comparisons,
                                srcCursor, tgtCursor, delta.getSource().getLines(), delta.getTarget().getLines());
                        int minSize = Math.min(delta.getSource().getLines().size(), delta.getTarget().getLines().size());
                        tgtCursor += minSize;
                        break;
                }
            }
            while (srcCursor < sourceLines.size() && tgtCursor < targetLines.size()) {
                comparisons.add(matchComparison(diff, sourceLines, targetLines, srcCursor, tgtCursor));
                srcCursor++;
                tgtCursor++;
            }
            while (srcCursor < sourceLines.size()) {
                comparisons.add(deleteComparison(diff, sourceLines.get(srcCursor), srcCursor));
                srcCursor++;
            }
            while (tgtCursor < targetLines.size()) {
                comparisons.add(insertComparison(diff, targetLines.get(tgtCursor), tgtCursor));
                tgtCursor++;
            }
        } catch (Exception ex) {
            log.warn("逐行相似度精确模式失败，使用降级估算 diff={}", diff, ex);
            return fallbackSnapshot(diff, sourceLines, targetLines);
        }
        return toSnapshot(comparisons);
    }

    private LineSimilaritySnapshot fallbackSnapshot(BlockDiff diff,
                                                    List<String> sourceLines,
                                                    List<String> targetLines) {
        List<LineComparison> comparisons = new ArrayList<>();
        int max = Math.max(sourceLines.size(), targetLines.size());
        for (int i = 0; i < max; i++) {
            String src = i < sourceLines.size() ? sourceLines.get(i) : null;
            String tgt = i < targetLines.size() ? targetLines.get(i) : null;
            if (src != null && tgt != null) {
                comparisons.add(changeComparison(diff, src, tgt, i, i));
            } else if (src != null) {
                comparisons.add(deleteComparison(diff, src, i));
            } else {
                comparisons.add(insertComparison(diff, tgt, i));
            }
        }
        return toSnapshot(comparisons);
    }

    private int handleInsert(BlockDiff diff,
                             List<String> targetLines,
                             List<LineComparison> comparisons,
                             int tgtCursor,
                             List<String> inserted) {
        if (CollectionUtils.isEmpty(inserted)) {
            return tgtCursor;
        }
        for (String line : inserted) {
            comparisons.add(insertComparison(diff, line, tgtCursor));
            tgtCursor++;
        }
        return tgtCursor;
    }

    private int handleDelete(BlockDiff diff,
                             List<String> sourceLines,
                             List<LineComparison> comparisons,
                             int srcCursor,
                             List<String> deleted) {
        if (CollectionUtils.isEmpty(deleted)) {
            return srcCursor;
        }
        for (String line : deleted) {
            comparisons.add(deleteComparison(diff, line, srcCursor));
            srcCursor++;
        }
        return srcCursor;
    }

    private int handleChange(BlockDiff diff,
                             List<String> sourceLines,
                             List<String> targetLines,
                             List<LineComparison> comparisons,
                             int srcCursor,
                             int tgtCursor,
                             List<String> srcSegment,
                             List<String> tgtSegment) {
        int srcSize = srcSegment == null ? 0 : srcSegment.size();
        int tgtSize = tgtSegment == null ? 0 : tgtSegment.size();
        int min = Math.min(srcSize, tgtSize);
        for (int i = 0; i < min; i++) {
            comparisons.add(changeComparison(diff, srcSegment.get(i), tgtSegment.get(i), srcCursor, tgtCursor));
            srcCursor++;
            tgtCursor++;
        }
        if (srcSize > min) {
            for (int i = min; i < srcSize; i++) {
                comparisons.add(deleteComparison(diff, srcSegment.get(i), srcCursor));
                srcCursor++;
            }
        }
        if (tgtSize > min) {
            for (int i = min; i < tgtSize; i++) {
                comparisons.add(insertComparison(diff, tgtSegment.get(i), tgtCursor));
                tgtCursor++;
            }
        }
        return srcCursor;
    }

    private LineComparison matchComparison(BlockDiff diff,
                                           List<String> sourceLines,
                                           List<String> targetLines,
                                           int srcIndex,
                                           int tgtIndex) {
        String sourceLine = sourceLines.get(srcIndex);
        String targetLine = targetLines.get(tgtIndex);
        double similarity = computeLineSimilarity(sourceLine, targetLine);
        return LineComparison.match(
                lineNumber(diff.getSourceStartLine(), srcIndex),
                lineNumber(diff.getTargetStartLine(), tgtIndex),
                similarity);
    }

    private LineComparison changeComparison(BlockDiff diff,
                                            String sourceLine,
                                            String targetLine,
                                            int srcIndex,
                                            int tgtIndex) {
        double similarity = computeLineSimilarity(sourceLine, targetLine);
        return LineComparison.change(
                lineNumber(diff.getSourceStartLine(), srcIndex),
                sanitizeLine(sourceLine),
                lineNumber(diff.getTargetStartLine(), tgtIndex),
                sanitizeLine(targetLine),
                similarity);
    }

    private LineComparison deleteComparison(BlockDiff diff, String sourceLine, int srcIndex) {
        return LineComparison.delete(
                lineNumber(diff.getSourceStartLine(), srcIndex),
                sanitizeLine(sourceLine));
    }

    private LineComparison insertComparison(BlockDiff diff, String targetLine, int tgtIndex) {
        return LineComparison.insert(
                lineNumber(diff.getTargetStartLine(), tgtIndex),
                sanitizeLine(targetLine));
    }

    private LineSimilaritySnapshot toSnapshot(List<LineComparison> comparisons) {
        if (comparisons.isEmpty()) {
            return LineSimilaritySnapshot.empty();
        }
        double sum = 0d;
        int weightedCount = 0;
        int matched = 0;
        for (LineComparison comparison : comparisons) {
            if (comparison == null) {
                continue;
            }
            sum += comparison.getSimilarity();
            weightedCount++;
            if (comparison.getType().isPaired()) {
                matched++;
            }
        }
        double similarity = weightedCount == 0 ? 100d : sum / weightedCount;
        return new LineSimilaritySnapshot(similarity, matched, weightedCount, comparisons);
    }

    private List<String> sanitize(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>(lines.size());
        for (String line : lines) {
            normalized.add(sanitizeLine(line));
        }
        return normalized;
    }

    private String sanitizeLine(String line) {
        return line == null ? "" : line.replace("\r\n", "\n");
    }

    private int lineNumber(int base, int offset) {
        if (base <= 0) {
            return offset + 1;
        }
        return base + offset;
    }

    private double computeLineSimilarity(String sourceLine, String targetLine) {
        if (!StringUtils.hasText(sourceLine) && !StringUtils.hasText(targetLine)) {
            return 100d;
        }
        if (!StringUtils.hasText(sourceLine) || !StringUtils.hasText(targetLine)) {
            return 0d;
        }
        List<String> left = Collections.singletonList(sourceLine);
        List<String> right = Collections.singletonList(targetLine);
        return DiffSimilarityCalculator.computeSimilarity(left, right);
    }
}
