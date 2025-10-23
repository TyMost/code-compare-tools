package com.example.codecompare.rebuild.stats;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 简化的差异片段 DTO，供 API 层与前端使用。
 */
public final class DiffSegmentDTO {

    private final DiffSegmentType type;
    private final int sourceStartLine;
    private final int targetStartLine;
    private final int changedLineCount;
    private final double similarity;
    private final List<String> sourceLines;
    private final List<String> targetLines;
    private final String sourceContent;
    private final String targetContent;
    private final Map<String, Object> metadata;

    public DiffSegmentDTO(DiffSegmentType type,
                          int sourceStartLine,
                          int targetStartLine,
                          int changedLineCount,
                          double similarity,
                          List<String> sourceLines,
                          List<String> targetLines,
                          String sourceContent,
                          String targetContent,
                          Map<String, Object> metadata) {
        this.type = type == null ? DiffSegmentType.CHANGE : type;
        this.sourceStartLine = Math.max(1, sourceStartLine);
        this.targetStartLine = Math.max(1, targetStartLine);
        this.changedLineCount = Math.max(0, changedLineCount);
        this.similarity = similarity;
        this.sourceLines = sourceLines == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(sourceLines));
        this.targetLines = targetLines == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(targetLines));
        this.sourceContent = sourceContent == null ? "" : sourceContent;
        this.targetContent = targetContent == null ? "" : targetContent;
        this.metadata = metadata == null ? Collections.<String, Object>emptyMap() : Collections.unmodifiableMap(metadata);
    }

    public static DiffSegmentDTO from(BlockDiff diff) {
        if (diff == null) {
            return new DiffSegmentDTO(DiffSegmentType.CHANGE, 1, 1, 0, 100d,
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    "",
                    "",
                    Collections.<String, Object>emptyMap());
        }
        return new DiffSegmentDTO(
                diff.getType(),
                diff.getSourceStartLine(),
                diff.getTargetStartLine(),
                diff.getChangedLineCount(),
                diff.getSimilarityScore(),
                diff.getSourceLines(),
                diff.getTargetLines(),
                diff.getSourceContent(),
                diff.getTargetContent(),
                diff.getMetadata()
        );
    }

    public DiffSegmentType getType() {
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
