package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.diff.model.DiffSummary;
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
            DiffSegmentType type = mapType(delta.getType());
            int sourceStart = delta.getSource().getPosition() + 1;
            int targetStart = delta.getTarget().getPosition() + 1;
            List<String> deltaSourceLines = new ArrayList<>(delta.getSource().getLines());
            List<String> deltaTargetLines = new ArrayList<>(delta.getTarget().getLines());
            int changed = Math.max(deltaSourceLines.size(), deltaTargetLines.size());
            int replacements = computeReplacements(deltaSourceLines, deltaTargetLines);
            double similarity = computeSimilarity(deltaSourceLines, deltaTargetLines);

            BlockDiff segment = BlockDiff.builder()
                    .type(type)
                    .sourceStartLine(sourceStart)
                    .targetStartLine(targetStart)
                    .changedLineCount(changed)
                    .replacements(replacements)
                    .similarityScore(similarity)
                    .sourceLines(deltaSourceLines)
                    .targetLines(deltaTargetLines)
                    .build();
            segments.add(segment);
        }

        List<BlockDiff> mergedSegments = mergeAdjacentSegments(segments);
        DiffSummary summary = buildSummary(mergedSegments);
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

    private int computeReplacements(List<String> sourceLines, List<String> targetLines) {
        int min = Math.min(sourceLines.size(), targetLines.size());
        int replacements = 0;
        for (int i = 0; i < min; i++) {
            String src = sourceLines.get(i);
            String tgt = targetLines.get(i);
            if (!equalsIgnoreLineEnding(src, tgt)) {
                replacements++;
            }
        }
        return replacements;
    }

    private double computeSimilarity(List<String> sourceLines, List<String> targetLines) {
        int max = Math.max(sourceLines.size(), targetLines.size());
        if (max == 0) {
            return 100d;
        }
        int equal = 0;
        int min = Math.min(sourceLines.size(), targetLines.size());
        for (int i = 0; i < min; i++) {
            if (equalsIgnoreLineEnding(sourceLines.get(i), targetLines.get(i))) {
                equal++;
            }
        }
        return (double) equal / (double) max * 100d;
    }

    private boolean equalsIgnoreLineEnding(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n");
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

    private DiffSummary buildSummary(List<BlockDiff> segments) {
        DiffSummary.Builder builder = DiffSummary.builder();
        int insertCount = 0;
        int deleteCount = 0;
        int changeCount = 0;
        int totalChangedLines = 0;
        int maxContinuous = 0;
        if (segments != null) {
            for (BlockDiff segment : segments) {
                if (segment == null) {
                    continue;
                }
                int changed = segment.getChangedLineCount();
                totalChangedLines = safeAdd(totalChangedLines, changed);
                maxContinuous = Math.max(maxContinuous, changed);
                switch (segment.getType()) {
                    case INSERT:
                        insertCount++;
                        break;
                    case DELETE:
                        deleteCount++;
                        break;
                    default:
                        changeCount++;
                        break;
                }
            }
        }
        return builder
                .insertSegments(insertCount)
                .deleteSegments(deleteCount)
                .changeSegments(changeCount)
                .totalChangedLines(totalChangedLines)
                .maxContinuousChangedLines(maxContinuous)
                .build();
    }
}
