package com.example.codecompare.rebuild.api.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 前端增量 diff 展示所需的片段视图。
 */
public final class DiffSegmentView {

    private final String type;
    private final int sourceStartLine;
    private final int targetStartLine;
    private final int changedLineCount;
    private final double similarity;
    private final List<String> sourceLines;
    private final List<String> targetLines;
    private final String sourceContent;
    private final String targetContent;
    private final Map<String, Object> metadata;

    public DiffSegmentView(String type,
                           int sourceStartLine,
                           int targetStartLine,
                           int changedLineCount,
                           double similarity,
                           List<String> sourceLines,
                           List<String> targetLines,
                           String sourceContent,
                           String targetContent,
                           Map<String, Object> metadata) {
        this.type = type;
        this.sourceStartLine = sourceStartLine;
        this.targetStartLine = targetStartLine;
        this.changedLineCount = changedLineCount;
        this.similarity = similarity;
        this.sourceLines = sourceLines == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(sourceLines));
        this.targetLines = targetLines == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(targetLines));
        this.sourceContent = sourceContent == null ? "" : sourceContent;
        this.targetContent = targetContent == null ? "" : targetContent;
        this.metadata = metadata == null ? Collections.<String, Object>emptyMap() : Collections.unmodifiableMap(new java.util.LinkedHashMap<String, Object>(metadata));
    }

    public String getType() {
        return type;
    }

    public int getSourceStartLine() {
        return sourceStartLine;
    }

    public int getTargetStartLine() {
        return targetStartLine;
    }

    public int getChangedLineCount() {
        return changedLineCount;
    }

    public double getSimilarity() {
        return similarity;
    }

    public List<String> getSourceLines() {
        return sourceLines;
    }

    public List<String> getTargetLines() {
        return targetLines;
    }

    public String getSourceContent() {
        return sourceContent;
    }

    public String getTargetContent() {
        return targetContent;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
