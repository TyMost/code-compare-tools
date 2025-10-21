package com.example.codecompare.rebuild.scanning.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Git diff hunk representation capturing the changed line ranges and raw content.
 */
public final class GitDiffHunk {

    private final Range oldRange;
    private final Range newRange;
    private final List<String> lines;
    private final long byteSize;

    private GitDiffHunk(Builder builder) {
        this.oldRange = Objects.requireNonNull(builder.oldRange, "oldRange must not be null");
        this.newRange = Objects.requireNonNull(builder.newRange, "newRange must not be null");
        this.lines = Collections.unmodifiableList(new ArrayList<>(builder.lines));
        this.byteSize = builder.byteSize < 0 ? 0 : builder.byteSize;
    }

    public Range getOldRange() {
        return oldRange;
    }

    public Range getNewRange() {
        return newRange;
    }

    /**
     * Raw diff lines in unified format, including leading context markers such as '+', '-', or ' '.
     */
    public List<String> getLines() {
        return lines;
    }

    public long getByteSize() {
        return byteSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Range oldRange = Range.empty();
        private Range newRange = Range.empty();
        private final List<String> lines = new ArrayList<>();
        private long byteSize;

        private Builder() {
        }

        public Builder oldRange(Range oldRange) {
            this.oldRange = oldRange == null ? Range.empty() : oldRange;
            return this;
        }

        public Builder newRange(Range newRange) {
            this.newRange = newRange == null ? Range.empty() : newRange;
            return this;
        }

        public Builder addLine(String line) {
            if (line != null) {
                this.lines.add(line);
            }
            return this;
        }

        public Builder lines(List<String> lines) {
            this.lines.clear();
            if (lines != null) {
                this.lines.addAll(lines);
            }
            return this;
        }

        public Builder byteSize(long byteSize) {
            this.byteSize = byteSize;
            return this;
        }

        public GitDiffHunk build() {
            return new GitDiffHunk(this);
        }
    }

    public static final class Range {
        private final int startLine;
        private final int lineCount;

        private Range(int startLine, int lineCount) {
            this.startLine = Math.max(startLine, 0);
            this.lineCount = Math.max(lineCount, 0);
        }

        public int getStartLine() {
            return startLine;
        }

        public int getLineCount() {
            return lineCount;
        }

        public static Range of(int startLine, int lineCount) {
            return new Range(startLine, lineCount);
        }

        private static Range empty() {
            return new Range(0, 0);
        }
    }
}
