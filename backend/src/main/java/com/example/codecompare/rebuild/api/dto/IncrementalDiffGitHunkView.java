package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single Git diff hunk with line range metadata.
 */
@JsonDeserialize(builder = IncrementalDiffGitHunkView.Builder.class)
public final class IncrementalDiffGitHunkView {

    private final int oldStartLine;
    private final int oldLineCount;
    private final int newStartLine;
    private final int newLineCount;
    private final List<String> lines;
    private final long byteSize;

    private IncrementalDiffGitHunkView(Builder builder) {
        this.oldStartLine = Math.max(0, builder.oldStartLine);
        this.oldLineCount = Math.max(0, builder.oldLineCount);
        this.newStartLine = Math.max(0, builder.newStartLine);
        this.newLineCount = Math.max(0, builder.newLineCount);
        this.lines = builder.lines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.lines));
        this.byteSize = Math.max(0L, builder.byteSize);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getOldStartLine() {
        return oldStartLine;
    }

    public int getOldLineCount() {
        return oldLineCount;
    }

    public int getNewStartLine() {
        return newStartLine;
    }

    public int getNewLineCount() {
        return newLineCount;
    }

    public List<String> getLines() {
        return lines;
    }

    public long getByteSize() {
        return byteSize;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private int oldStartLine;
        private int oldLineCount;
        private int newStartLine;
        private int newLineCount;
        private List<String> lines;
        private long byteSize;

        public Builder oldStartLine(@JsonProperty("oldStartLine") int oldStartLine) {
            this.oldStartLine = oldStartLine;
            return this;
        }

        public Builder oldLineCount(@JsonProperty("oldLineCount") int oldLineCount) {
            this.oldLineCount = oldLineCount;
            return this;
        }

        public Builder newStartLine(@JsonProperty("newStartLine") int newStartLine) {
            this.newStartLine = newStartLine;
            return this;
        }

        public Builder newLineCount(@JsonProperty("newLineCount") int newLineCount) {
            this.newLineCount = newLineCount;
            return this;
        }

        public Builder lines(@JsonProperty("lines") List<String> lines) {
            this.lines = lines;
            return this;
        }

        public Builder byteSize(@JsonProperty("byteSize") long byteSize) {
            this.byteSize = byteSize;
            return this;
        }

        public IncrementalDiffGitHunkView build() {
            return new IncrementalDiffGitHunkView(this);
        }
    }
}
