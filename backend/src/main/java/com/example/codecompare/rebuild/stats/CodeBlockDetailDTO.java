package com.example.codecompare.rebuild.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 代码块详情 DTO。
 */
public final class CodeBlockDetailDTO {

    private final String id;
    private final String comparisonId;
    private final String sourceProjectCode;
    private final String targetProjectCode;
    private final String filePath;
    private final int startLine;
    private final int endLine;
    private final String oldCode;
    private final String newCode;
    private final String status;
    private final String statusLabel;
    private final List<String> categoryKeys;
    private final boolean aiSuggestionEnabled;
    private final String previousId;
    private final String nextId;

    public CodeBlockDetailDTO(String id,
                              String comparisonId,
                              String sourceProjectCode,
                              String targetProjectCode,
                              String filePath,
                              int startLine,
                              int endLine,
                              String oldCode,
                              String newCode,
                              String status,
                              String statusLabel,
                              List<String> categoryKeys,
                              boolean aiSuggestionEnabled,
                              String previousId,
                              String nextId) {
        this.id = id;
        this.comparisonId = comparisonId;
        this.sourceProjectCode = sourceProjectCode;
        this.targetProjectCode = targetProjectCode;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.oldCode = oldCode;
        this.newCode = newCode;
        this.status = status;
        this.statusLabel = statusLabel;
        this.categoryKeys = categoryKeys == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(categoryKeys));
        this.aiSuggestionEnabled = aiSuggestionEnabled;
        this.previousId = previousId;
        this.nextId = nextId;
    }

    public String getId() {
        return id;
    }

    public String getComparisonId() {
        return comparisonId;
    }

    public String getSourceProjectCode() {
        return sourceProjectCode;
    }

    public String getTargetProjectCode() {
        return targetProjectCode;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getOldCode() {
        return oldCode;
    }

    public String getNewCode() {
        return newCode;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public List<String> getCategoryKeys() {
        return categoryKeys;
    }

    public boolean isAiSuggestionEnabled() {
        return aiSuggestionEnabled;
    }

    public String getPreviousId() {
        return previousId;
    }

    public String getNextId() {
        return nextId;
    }
}
