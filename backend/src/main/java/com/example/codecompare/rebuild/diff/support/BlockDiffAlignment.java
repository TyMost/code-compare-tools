package com.example.codecompare.rebuild.diff.support;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper that converts {@link BlockDiff} line collections into row-aligned representations
 * suitable for side-by-side diff rendering.
 */
public final class BlockDiffAlignment {

    private BlockDiffAlignment() {
    }

    public static List<Line> align(BlockDiff diff) {
        if (diff == null) {
            return Collections.emptyList();
        }
        List<String> sourceLines = normalize(diff.getSourceLines());
        List<String> targetLines = normalize(diff.getTargetLines());
        int sourceStart = Math.max(1, diff.getSourceStartLine());
        int targetStart = Math.max(1, diff.getTargetStartLine());
        if (sourceLines.isEmpty() && targetLines.isEmpty()) {
            return Collections.emptyList();
        }
        Patch<String> patch = DiffUtils.diff(sourceLines, targetLines);
        if (patch.getDeltas().isEmpty()) {
            return zipFallback(sourceLines, targetLines, sourceStart, targetStart);
        }
        List<Line> rows = new ArrayList<>();
        int sourceIndex = 0;
        int targetIndex = 0;
        int sourceLine = sourceStart;
        int targetLine = targetStart;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int nextSource = delta.getSource().getPosition();
            int nextTarget = delta.getTarget().getPosition();
            while (sourceIndex < nextSource && targetIndex < nextTarget) {
                rows.add(new Line(
                        LineType.CONTEXT,
                        sourceLine++,
                        sourceLines.get(sourceIndex++),
                        targetLine++,
                        targetLines.get(targetIndex++)
                ));
            }
            appendDelta(rows, delta, sourceLine, targetLine);
            sourceIndex = delta.getSource().getPosition() + delta.getSource().size();
            targetIndex = delta.getTarget().getPosition() + delta.getTarget().size();
            sourceLine = sourceStart + sourceIndex;
            targetLine = targetStart + targetIndex;
        }
        while (sourceIndex < sourceLines.size() && targetIndex < targetLines.size()) {
            rows.add(new Line(
                    LineType.CONTEXT,
                    sourceLine++,
                    sourceLines.get(sourceIndex++),
                    targetLine++,
                    targetLines.get(targetIndex++)
            ));
        }
        while (sourceIndex < sourceLines.size()) {
            rows.add(new Line(
                    LineType.REMOVE,
                    sourceLine++,
                    sourceLines.get(sourceIndex++),
                    null,
                    null
            ));
        }
        while (targetIndex < targetLines.size()) {
            rows.add(new Line(
                    LineType.ADD,
                    null,
                    null,
                    targetLine++,
                    targetLines.get(targetIndex++)
            ));
        }
        return rows;
    }

    private static void appendDelta(List<Line> rows,
                                    AbstractDelta<String> delta,
                                    int sourceLine,
                                    int targetLine) {
        List<String> sourceChunk = delta.getSource().getLines();
        List<String> targetChunk = delta.getTarget().getLines();
        switch (delta.getType()) {
            case DELETE:
                for (String line : sourceChunk) {
                    rows.add(new Line(
                            LineType.REMOVE,
                            sourceLine++,
                            line,
                            null,
                            null
                    ));
                }
                break;
            case INSERT:
                for (String line : targetChunk) {
                    rows.add(new Line(
                            LineType.ADD,
                            null,
                            null,
                            targetLine++,
                            line
                    ));
                }
                break;
            case CHANGE:
                int shared = Math.min(sourceChunk.size(), targetChunk.size());
                for (int i = 0; i < shared; i += 1) {
                    String source = sourceChunk.get(i);
                    String target = targetChunk.get(i);
                    LineType type = source.equals(target) ? LineType.CONTEXT : LineType.CHANGE;
                    rows.add(new Line(
                            type,
                            sourceLine++,
                            source,
                            targetLine++,
                            target
                    ));
                }
                for (int i = shared; i < sourceChunk.size(); i += 1) {
                    rows.add(new Line(
                            LineType.REMOVE,
                            sourceLine++,
                            sourceChunk.get(i),
                            null,
                            null
                    ));
                }
                for (int i = shared; i < targetChunk.size(); i += 1) {
                    rows.add(new Line(
                            LineType.ADD,
                            null,
                            null,
                            targetLine++,
                            targetChunk.get(i)
                    ));
                }
                break;
            default:
                // fallback to zipped rows
                rows.addAll(zipFallback(sourceChunk, targetChunk, sourceLine, targetLine));
                break;
        }
    }

    private static List<Line> zipFallback(List<String> sourceLines,
                                          List<String> targetLines,
                                          int sourceStart,
                                          int targetStart) {
        if (CollectionUtils.isEmpty(sourceLines) && CollectionUtils.isEmpty(targetLines)) {
            return Collections.emptyList();
        }
        List<Line> rows = new ArrayList<>();
        int length = Math.max(size(sourceLines), size(targetLines));
        int sourceLine = sourceStart;
        int targetLine = targetStart;
        for (int i = 0; i < length; i += 1) {
            String source = i < size(sourceLines) ? sourceLines.get(i) : null;
            String target = i < size(targetLines) ? targetLines.get(i) : null;
            LineType type;
            boolean hasSource = source != null;
            boolean hasTarget = target != null;
            if (hasSource && hasTarget) {
                type = source.equals(target) ? LineType.CONTEXT : LineType.CHANGE;
            } else if (hasSource) {
                type = LineType.REMOVE;
            } else if (hasTarget) {
                type = LineType.ADD;
            } else {
                type = LineType.CONTEXT;
            }
            rows.add(new Line(
                    hasSource ? sourceLine++ : null,
                    source,
                    hasTarget ? targetLine++ : null,
                    target,
                    type
            ));
        }
        return rows;
    }

    private static int size(List<String> values) {
        return values == null ? 0 : values.size();
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    public enum LineType {
        CONTEXT,
        ADD,
        REMOVE,
        CHANGE
    }

    public static final class Line {
        private final LineType type;
        private final Integer sourceLine;
        private final String sourceText;
        private final Integer targetLine;
        private final String targetText;

        public Line(LineType type,
                    Integer sourceLine,
                    String sourceText,
                    Integer targetLine,
                    String targetText) {
            this.type = type;
            this.sourceLine = sourceLine;
            this.sourceText = sourceText == null ? "" : sourceText;
            this.targetLine = targetLine;
            this.targetText = targetText == null ? "" : targetText;
        }

        public Line(Integer sourceLine,
                    String sourceText,
                    Integer targetLine,
                    String targetText,
                    LineType type) {
            this(type, sourceLine, sourceText, targetLine, targetText);
        }

        public LineType getType() {
            return type;
        }

        public Integer getSourceLine() {
            return sourceLine;
        }

        public String getSourceText() {
            return sourceText;
        }

        public Integer getTargetLine() {
            return targetLine;
        }

        public String getTargetText() {
            return targetText;
        }
    }
}
