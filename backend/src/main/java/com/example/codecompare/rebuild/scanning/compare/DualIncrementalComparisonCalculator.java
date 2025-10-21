package com.example.codecompare.rebuild.scanning.compare;

import com.example.codecompare.rebuild.api.dto.DualIncrementalComparisonBlockView;
import com.example.codecompare.rebuild.api.dto.DualIncrementalComparisonView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileDetailView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffGitDiffView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffGitHunkView;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Calculates similarity metrics between two incremental Git diffs.
 */
@Component
public class DualIncrementalComparisonCalculator {

    public Optional<DualIncrementalComparisonView> compare(IncrementalDiffFileDetailView source,
                                                           IncrementalDiffFileDetailView target) {
        IncrementalDiffGitDiffView sourceDiff = source == null ? null : source.getGitDiff();
        IncrementalDiffGitDiffView targetDiff = target == null ? null : target.getGitDiff();
        List<HunkSummary> sourceHunks = summarise(sourceDiff);
        List<HunkSummary> targetHunks = summarise(targetDiff);
        if (sourceHunks.isEmpty() && targetHunks.isEmpty()) {
            return Optional.empty();
        }

        int sameLineCount = Math.min(
                sourceDiff == null ? 0 : sourceDiff.getSameLineCount(),
                targetDiff == null ? 0 : targetDiff.getSameLineCount());

        List<DualIncrementalComparisonBlockView> blocks = new ArrayList<DualIncrementalComparisonBlockView>();
        double similarityNumerator = sameLineCount;
        int totalChangedLines = sameLineCount;

        int paired = Math.min(sourceHunks.size(), targetHunks.size());
        for (int index = 0; index < paired; index++) {
            BlockComputation computation = computeBlock(index, sourceHunks.get(index), targetHunks.get(index));
            blocks.add(computation.getView());
            similarityNumerator += computation.getSimilarityContribution();
            totalChangedLines += computation.getReferenceLineCount();
        }

        if (sourceHunks.size() > paired) {
            for (int index = paired; index < sourceHunks.size(); index++) {
                BlockComputation computation = computeUnmatchedBlock(index, sourceHunks.get(index), true);
                blocks.add(computation.getView());
                totalChangedLines += computation.getReferenceLineCount();
            }
        }
        if (targetHunks.size() > paired) {
            for (int index = paired; index < targetHunks.size(); index++) {
                BlockComputation computation = computeUnmatchedBlock(index, targetHunks.get(index), false);
                blocks.add(computation.getView());
                totalChangedLines += computation.getReferenceLineCount();
            }
        }

        double similarityPercent = totalChangedLines > 0
                ? (similarityNumerator / totalChangedLines) * 100d
                : 0d;

        DualIncrementalComparisonView view = DualIncrementalComparisonView.builder()
                .fileSimilarity(round(similarityPercent))
                .sameLineCount(sameLineCount)
                .totalChangedLines(totalChangedLines)
                .blocks(blocks)
                .build();
        return Optional.of(view);
    }

    private List<HunkSummary> summarise(IncrementalDiffGitDiffView diff) {
        if (diff == null || CollectionUtils.isEmpty(diff.getHunks())) {
            return Collections.emptyList();
        }
        List<HunkSummary> summaries = new ArrayList<HunkSummary>();
        for (IncrementalDiffGitHunkView hunk : diff.getHunks()) {
            if (hunk == null || CollectionUtils.isEmpty(hunk.getLines())) {
                continue;
            }
            List<String> added = new ArrayList<String>();
            List<String> removed = new ArrayList<String>();
            int changed = 0;
            for (String line : hunk.getLines()) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                char marker = line.charAt(0);
                String payload = line.substring(1);
                if (marker == '+') {
                    added.add(payload);
                    changed++;
                } else if (marker == '-') {
                    removed.add(payload);
                    changed++;
                }
            }
            if (!added.isEmpty() || !removed.isEmpty()) {
                summaries.add(new HunkSummary(added, removed, changed));
            }
        }
        return summaries;
    }

    private BlockComputation computeBlock(int index, HunkSummary source, HunkSummary target) {
        int referenceLines = Math.max(Math.max(source.getChangedLineCount(), target.getChangedLineCount()), 1);
        double similarity = computeLineSimilarity(source.getAddedLines(), target.getAddedLines());
        double contribution = (similarity / 100d) * referenceLines;
        DualIncrementalComparisonBlockView view = DualIncrementalComparisonBlockView.builder()
                .index(index + 1)
                .similarity(round(similarity))
                .sourceChangedLines(source.getChangedLineCount())
                .targetChangedLines(target.getChangedLineCount())
                .referenceLineCount(referenceLines)
                .build();
        return new BlockComputation(view, contribution, referenceLines);
    }

    private BlockComputation computeUnmatchedBlock(int index, HunkSummary summary, boolean sourceOnly) {
        int referenceLines = Math.max(summary.getChangedLineCount(), 1);
        DualIncrementalComparisonBlockView.Builder builder = DualIncrementalComparisonBlockView.builder()
                .index(index + 1)
                .similarity(0d)
                .sourceChangedLines(sourceOnly ? summary.getChangedLineCount() : 0)
                .targetChangedLines(sourceOnly ? 0 : summary.getChangedLineCount())
                .referenceLineCount(referenceLines);
        if (sourceOnly) {
            builder.sourceOnly(true);
        } else {
            builder.targetOnly(true);
        }
        return new BlockComputation(builder.build(), 0d, referenceLines);
    }

    private double computeLineSimilarity(List<String> left, List<String> right) {
        if (CollectionUtils.isEmpty(left) && CollectionUtils.isEmpty(right)) {
            return 100d;
        }
        if (CollectionUtils.isEmpty(left) || CollectionUtils.isEmpty(right)) {
            return 0d;
        }
        int lcs = longestCommonSubsequence(left, right);
        int denominator = Math.max(left.size(), right.size());
        if (denominator <= 0) {
            return 0d;
        }
        return ((double) lcs / (double) denominator) * 100d;
    }

    private int longestCommonSubsequence(List<String> left, List<String> right) {
        int m = left.size();
        int n = right.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (equalsIgnoreWhitespace(left.get(i - 1), right.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[m][n];
    }

    private boolean equalsIgnoreWhitespace(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static final class HunkSummary {
        private final List<String> addedLines;
        private final List<String> removedLines;
        private final int changedLineCount;

        private HunkSummary(List<String> addedLines, List<String> removedLines, int changedLineCount) {
            this.addedLines = addedLines == null ? Collections.<String>emptyList() : addedLines;
            this.removedLines = removedLines == null ? Collections.<String>emptyList() : removedLines;
            this.changedLineCount = Math.max(0, changedLineCount);
        }

        private List<String> getAddedLines() {
            return addedLines;
        }

        @SuppressWarnings("unused")
        private List<String> getRemovedLines() {
            return removedLines;
        }

        private int getChangedLineCount() {
            return changedLineCount;
        }
    }

    private static final class BlockComputation {
        private final DualIncrementalComparisonBlockView view;
        private final double similarityContribution;
        private final int referenceLineCount;

        private BlockComputation(DualIncrementalComparisonBlockView view,
                                 double similarityContribution,
                                 int referenceLineCount) {
            this.view = view;
            this.similarityContribution = similarityContribution;
            this.referenceLineCount = referenceLineCount;
        }

        private DualIncrementalComparisonBlockView getView() {
            return view;
        }

        private double getSimilarityContribution() {
            return similarityContribution;
        }

        private int getReferenceLineCount() {
            return referenceLineCount;
        }
    }
}
