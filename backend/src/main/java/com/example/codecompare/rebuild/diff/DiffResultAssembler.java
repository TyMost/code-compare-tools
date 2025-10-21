package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.diff.model.DiffSummary;
import com.example.codecompare.rebuild.diff.support.DiffSimilarityCalculator;
import com.example.codecompare.rebuild.diff.support.DiffSummaryCalculator;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将 {@link Patch} 结果转换为对外的差异模型。
 */
@Component
public class DiffResultAssembler {

    public DiffResult assemble(List<String> sourceLines, List<String> targetLines, Patch<String> patch) {
        if (patch == null || patch.getDeltas().isEmpty()) {
            return DiffResult.empty();
        }
        List<BlockDiff> segments = new ArrayList<>();

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            if (delta.getType() == DeltaType.CHANGE) {
                segments.addAll(splitChangeDelta(delta));
            } else {
                segments.add(createSegment(
                        mapType(delta.getType()),
                        delta.getSource().getPosition() + 1,
                        delta.getTarget().getPosition() + 1,
                        delta.getSource().getLines(),
                        delta.getTarget().getLines()));
            }
        }

        List<BlockDiff> mergedSegments = mergeAdjacentSegments(segments);
        DiffSummary summary = DiffSummaryCalculator.fromSegments(mergedSegments);
        return new DiffResult(mergedSegments, summary);
    }

    private DiffSegmentType mapType(DeltaType deltaType) {
        if (deltaType == null) {
            return DiffSegmentType.CHANGE;
        }
        switch (deltaType) {
            case DELETE:
                return DiffSegmentType.DELETE;
            case INSERT:
                return DiffSegmentType.INSERT;
            default:
                return DiffSegmentType.CHANGE;
        }
    }

    private List<BlockDiff> mergeAdjacentSegments(List<BlockDiff> segments) {
        if (segments == null || segments.size() < 2) {
            return segments == null ? Collections.emptyList() : segments;
        }
        List<BlockDiff> merged = new ArrayList<>(segments.size());
        BlockDiff current = null;
        for (BlockDiff candidate : segments) {
            if (current == null) {
                current = candidate;
                continue;
            }
            if (canMerge(current, candidate)) {
                current = mergeSegments(current, candidate);
            } else {
                merged.add(current);
                current = candidate;
            }
        }
        if (current != null) {
            merged.add(current);
        }
        return merged;
    }

    private boolean canMerge(BlockDiff left, BlockDiff right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getType() != right.getType()) {
            return false;
        }
        switch (left.getType()) {
            case INSERT:
                return areSequential(left.getTargetStartLine(), left.getTargetLines(),
                        right.getTargetStartLine(), right.getTargetLines());
            case DELETE:
                return areSequential(left.getSourceStartLine(), left.getSourceLines(),
                        right.getSourceStartLine(), right.getSourceLines());
            default:
                return areSequential(left.getSourceStartLine(), left.getSourceLines(),
                        right.getSourceStartLine(), right.getSourceLines())
                        && areSequential(left.getTargetStartLine(), left.getTargetLines(),
                        right.getTargetStartLine(), right.getTargetLines());
        }
    }

    private boolean areSequential(int start, List<String> lines, int nextStart, List<String> nextLines) {
        int length = lines == null ? 0 : lines.size();
        int expectedNextStart = length == 0 ? start : start + length;
        if (nextLines == null || nextLines.isEmpty()) {
            return expectedNextStart == nextStart || (length == 0 && start == nextStart);
        }
        return expectedNextStart == nextStart;
    }

    private BlockDiff mergeSegments(BlockDiff left, BlockDiff right) {
        List<String> mergedSourceLines = mergeLines(left.getSourceLines(), right.getSourceLines());
        List<String> mergedTargetLines = mergeLines(left.getTargetLines(), right.getTargetLines());
        int mergedChanged = safeAdd(left.getChangedLineCount(), right.getChangedLineCount());
        int mergedReplacements = safeAdd(left.getReplacements(), right.getReplacements());
        double mergedSimilarity = mergeSimilarity(left, right, mergedChanged);

        return BlockDiff.from(left)
                .changedLineCount(mergedChanged)
                .replacements(mergedReplacements)
                .similarityScore(mergedSimilarity)
                .sourceLines(mergedSourceLines)
                .targetLines(mergedTargetLines)
                .sourceContent(null)
                .targetContent(null)
                .diffMetrics(null)
                .build();
    }

    private List<String> mergeLines(List<String> left, List<String> right) {
        boolean leftEmpty = left == null || left.isEmpty();
        boolean rightEmpty = right == null || right.isEmpty();
        if (leftEmpty && rightEmpty) {
            return Collections.emptyList();
        }
        if (leftEmpty) {
            return new ArrayList<>(right);
        }
        if (rightEmpty) {
            return new ArrayList<>(left);
        }
        List<String> merged = new ArrayList<>(left.size() + right.size());
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }

    private int safeAdd(int left, int right) {
        long sum = (long) left + (long) right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }

    private double mergeSimilarity(BlockDiff left, BlockDiff right, int totalChanged) {
        int leftWeight = Math.max(1, left.getChangedLineCount());
        int rightWeight = Math.max(1, right.getChangedLineCount());
        int denominator = leftWeight + rightWeight;
        if (denominator == 0 || totalChanged <= 0) {
            return right.getSimilarityScore();
        }
        double weighted = (left.getSimilarityScore() * leftWeight) + (right.getSimilarityScore() * rightWeight);
        return weighted / denominator;
    }

    private List<BlockDiff> splitChangeDelta(AbstractDelta<String> delta) {
        List<BlockDiff> segments = new ArrayList<>();
        List<String> sourceLines = new ArrayList<>(delta.getSource().getLines());
        List<String> targetLines = new ArrayList<>(delta.getTarget().getLines());
        int sourceStart = delta.getSource().getPosition() + 1;
        int targetStart = delta.getTarget().getPosition() + 1;
        int overlap = Math.min(sourceLines.size(), targetLines.size());

        if (overlap > 0) {
            segments.add(createSegment(
                    DiffSegmentType.CHANGE,
                    sourceStart,
                    targetStart,
                    sourceLines.subList(0, overlap),
                    targetLines.subList(0, overlap)));
        }

        if (targetLines.size() > overlap) {
            segments.add(createSegment(
                    DiffSegmentType.INSERT,
                    sourceStart + overlap,
                    targetStart + overlap,
                    Collections.emptyList(),
                    targetLines.subList(overlap, targetLines.size())));
        } else if (sourceLines.size() > overlap) {
            segments.add(createSegment(
                    DiffSegmentType.DELETE,
                    sourceStart + overlap,
                    targetStart + overlap,
                    sourceLines.subList(overlap, sourceLines.size()),
                    Collections.emptyList()));
        }

        return segments;
    }

    private BlockDiff createSegment(DiffSegmentType type,
                                    int sourceStart,
                                    int targetStart,
                                    List<String> sourceLines,
                                    List<String> targetLines) {
        List<String> normalizedSource = sourceLines == null
                ? Collections.emptyList()
                : new ArrayList<>(sourceLines);
        List<String> normalizedTarget = targetLines == null
                ? Collections.emptyList()
                : new ArrayList<>(targetLines);
        int changed = Math.max(normalizedSource.size(), normalizedTarget.size());
        int replacements = DiffSimilarityCalculator.computeReplacements(normalizedSource, normalizedTarget);
        double similarity = DiffSimilarityCalculator.computeSimilarity(normalizedSource, normalizedTarget);

        return BlockDiff.builder()
                .type(type)
                .sourceStartLine(sourceStart)
                .targetStartLine(targetStart)
                .changedLineCount(changed)
                .replacements(replacements)
                .similarityScore(similarity)
                .sourceLines(normalizedSource)
                .targetLines(normalizedTarget)
                .build();
    }

}
