package com.example.codecompare.rebuild.scanning.compare;

import com.example.codecompare.rebuild.api.dto.DualIncrementalComparisonBlockView;
import com.example.codecompare.rebuild.api.dto.DualIncrementalComparisonView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileDetailView;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calculates similarity metrics between two incremental Git diffs by comparing classified change blocks.
 */
@Component
public class DualIncrementalComparisonCalculator {

    private final IncrementalChangeExtractor extractor;

    public DualIncrementalComparisonCalculator(IncrementalChangeExtractor extractor) {
        this.extractor = extractor;
    }

    public Optional<DualIncrementalComparisonView> compare(IncrementalDiffFileDetailView sourceDetail,
                                                           IncrementalDiffFileDetailView targetDetail) {
        IncrementalChange sourceChange = extractor.extract(sourceDetail);
        IncrementalChange targetChange = extractor.extract(targetDetail);

        if (sourceChange.isEmpty() && targetChange.isEmpty()) {
            return Optional.empty();
        }

        List<IncrementalCodeBlock> sourceBlocks = sourceChange.getBlocks();
        List<IncrementalCodeBlock> targetBlocks = targetChange.getBlocks();
        boolean[] targetMatched = new boolean[targetBlocks.size()];

        int sameLineCount = Math.min(
                sourceChange.getGitStats().getSameLineCount(),
                targetChange.getGitStats().getSameLineCount());

        double similarityAccumulator = sameLineCount;
        int totalChangedLines = sameLineCount;
        int blockIndex = 1;
        double sourceCoveredLines = 0d;
        double targetCoveredLines = 0d;
        int sourceChangedLineTotal = 0;
        int targetChangedLineTotal = 0;

        List<DualIncrementalComparisonBlockView> blocks = new ArrayList<DualIncrementalComparisonBlockView>();

        for (int s = 0; s < sourceBlocks.size(); s++) {
            IncrementalCodeBlock sourceBlock = sourceBlocks.get(s);
            MatchResult match = findBestMatch(sourceBlock, targetBlocks, targetMatched);
            int sourceChangedLines = Math.max(1, sourceBlock.getChangedLines());
            sourceChangedLineTotal += sourceChangedLines;
            if (match.getTargetIndex() >= 0) {
                targetMatched[match.getTargetIndex()] = true;
                IncrementalCodeBlock targetBlock = targetBlocks.get(match.getTargetIndex());
                int targetChangedLines = Math.max(1, targetBlock.getChangedLines());
                targetChangedLineTotal += targetChangedLines;
                int referenceLines = Math.max(sourceBlock.getChangedLines(), targetBlock.getChangedLines());
                if (referenceLines <= 0) {
                    referenceLines = 1;
                }
                double similarityRatio = match.getSimilarity() / 100d;
                similarityAccumulator += similarityRatio * referenceLines;
                totalChangedLines += referenceLines;
                sourceCoveredLines += sourceChangedLines * similarityRatio;
                targetCoveredLines += targetChangedLines * similarityRatio;
                double sourceCoveragePercent = coveragePercent(sourceChangedLines * similarityRatio, sourceChangedLines);
                double targetCoveragePercent = coveragePercent(targetChangedLines * similarityRatio, targetChangedLines);
                blocks.add(buildBlockView(blockIndex++,
                        sourceBlock,
                        targetBlock,
                        referenceLines,
                        match.getSimilarity(),
                        "MATCHED",
                        sourceChange.getChangeType(),
                        targetChange.getChangeType(),
                        round(sourceCoveragePercent),
                        round(targetCoveragePercent)));
            } else {
                int referenceLines = Math.max(1, sourceBlock.getChangedLines());
                totalChangedLines += referenceLines;
                blocks.add(buildBlockView(blockIndex++,
                        sourceBlock,
                        null,
                        referenceLines,
                        0d,
                        "SOURCE_ONLY",
                        sourceChange.getChangeType(),
                        IncrementalChangeType.NONE,
                        0d,
                        0d));
            }
        }

        for (int t = 0; t < targetBlocks.size(); t++) {
            if (targetMatched[t]) {
                continue;
            }
            IncrementalCodeBlock targetBlock = targetBlocks.get(t);
            int targetChangedLines = Math.max(1, targetBlock.getChangedLines());
            targetChangedLineTotal += targetChangedLines;
            int referenceLines = Math.max(1, targetBlock.getChangedLines());
            totalChangedLines += referenceLines;
            blocks.add(buildBlockView(blockIndex++,
                    null,
                    targetBlock,
                    referenceLines,
                    0d,
                    "TARGET_ONLY",
                    IncrementalChangeType.NONE,
                    targetChange.getChangeType(),
                    0d,
                    0d));
        }

        double fileSimilarityPercent = totalChangedLines > 0
                ? (similarityAccumulator / (double) totalChangedLines) * 100d
                : 0d;
        double coverageAtoB = coveragePercent(sourceCoveredLines, sourceChangedLineTotal);
        double coverageBtoA = coveragePercent(targetCoveredLines, targetChangedLineTotal);

        DualIncrementalComparisonView view = DualIncrementalComparisonView.builder()
                .fileSimilarity(round(fileSimilarityPercent))
                .sameLineCount(sameLineCount)
                .totalChangedLines(totalChangedLines)
                .coverageAtoB(round(coverageAtoB))
                .coverageBtoA(round(coverageBtoA))
                .blocks(blocks)
                .build();
        return Optional.of(view);
    }

    private DualIncrementalComparisonBlockView buildBlockView(int index,
                                                              IncrementalCodeBlock sourceBlock,
                                                              IncrementalCodeBlock targetBlock,
                                                              int referenceLines,
                                                              double similarity,
                                                              String matchStatus,
                                                              IncrementalChangeType sourceChangeType,
                                                              IncrementalChangeType targetChangeType,
                                                              double sourceCoveragePercent,
                                                              double targetCoveragePercent) {
        String blockType = resolveBlockType(sourceBlock, targetBlock);
        String changeType = formatChangeType(sourceChangeType, targetChangeType);

        DualIncrementalComparisonBlockView.Builder builder = DualIncrementalComparisonBlockView.builder()
                .index(index)
                .similarity(round(similarity))
                .referenceLineCount(referenceLines)
                .blockType(blockType)
                .changeType(changeType)
                .matchStatus(matchStatus)
                .sourceCoveragePercent(sourceCoveragePercent)
                .targetCoveragePercent(targetCoveragePercent);

        if (sourceBlock != null) {
            builder.sourceChangedLines(sourceBlock.getChangedLines());
        }
        if (targetBlock != null) {
            builder.targetChangedLines(targetBlock.getChangedLines());
        }
        if ("SOURCE_ONLY".equals(matchStatus)) {
            builder.sourceOnly(true);
        }
        if ("TARGET_ONLY".equals(matchStatus)) {
            builder.targetOnly(true);
        }
        return builder.build();
    }

    private String resolveBlockType(IncrementalCodeBlock sourceBlock, IncrementalCodeBlock targetBlock) {
        if (sourceBlock != null) {
            return sourceBlock.getBlockType().name();
        }
        if (targetBlock != null) {
            return targetBlock.getBlockType().name();
        }
        return IncrementalBlockType.UNKNOWN.name();
    }

    private String formatChangeType(IncrementalChangeType sourceType, IncrementalChangeType targetType) {
        String left = sourceType == null ? IncrementalChangeType.NONE.name() : sourceType.name();
        String right = targetType == null ? IncrementalChangeType.NONE.name() : targetType.name();
        return left + "/" + right;
    }

    private MatchResult findBestMatch(IncrementalCodeBlock sourceBlock,
                                      List<IncrementalCodeBlock> targetBlocks,
                                      boolean[] matched) {
        double bestSimilarity = -1d;
        int bestIndex = -1;
        boolean bestSameType = false;

        for (int i = 0; i < targetBlocks.size(); i++) {
            if (matched[i]) {
                continue;
            }
            IncrementalCodeBlock candidate = targetBlocks.get(i);
            double similarity = computeLineSimilarity(sourceBlock.getLines(), candidate.getLines());
            boolean sameType = sourceBlock.getBlockType() == candidate.getBlockType();
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestIndex = i;
                bestSameType = sameType;
            } else if (similarity == bestSimilarity && sameType && !bestSameType) {
                bestIndex = i;
                bestSameType = true;
            }
        }
        if (bestSimilarity < 0d) {
            bestSimilarity = 0d;
        }
        return new MatchResult(bestIndex, bestSimilarity);
    }

    private double computeLineSimilarity(List<String> left, List<String> right) {
        if (CollectionUtils.isEmpty(left) && CollectionUtils.isEmpty(right)) {
            return 100d;
        }
        if (CollectionUtils.isEmpty(left) || CollectionUtils.isEmpty(right)) {
            return 0d;
        }
        int[][] dp = new int[left.size() + 1][right.size() + 1];
        for (int i = 1; i <= left.size(); i++) {
            for (int j = 1; j <= right.size(); j++) {
                if (equalsIgnoreWhitespace(left.get(i - 1), right.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        int lcs = dp[left.size()][right.size()];
        int denominator = Math.max(left.size(), right.size());
        if (denominator <= 0) {
            return 0d;
        }
        return ((double) lcs / (double) denominator) * 100d;
    }

    private boolean equalsIgnoreWhitespace(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }

    private double coveragePercent(double coveredLines, int totalChangedLines) {
        if (totalChangedLines <= 0) {
            return 100d;
        }
        double ratio = coveredLines / (double) totalChangedLines;
        if (ratio < 0d) {
            ratio = 0d;
        }
        if (ratio > 1d) {
            ratio = 1d;
        }
        return ratio * 100d;
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static final class MatchResult {
        private final int targetIndex;
        private final double similarity;

        private MatchResult(int targetIndex, double similarity) {
            this.targetIndex = targetIndex;
            this.similarity = similarity;
        }

        private int getTargetIndex() {
            return targetIndex;
        }

        private double getSimilarity() {
            return similarity;
        }
    }
}
