package com.example.codecompare.rebuild.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 代码块列表返回条目 DTO。
 */
public final class CodeBlockItemDTO {

    private final String id;
    private final String filePath;
    private final int startLine;
    private final int endLine;
    private final String codeSnippet;
    private final List<CategoryFilterDTO> categories;
    private final String status;
    private final String statusLabel;
    private final String statusColor;

    public CodeBlockItemDTO(String id,
                            String filePath,
                            int startLine,
                            int endLine,
                            String codeSnippet,
                            List<CategoryFilterDTO> categories,
                            String status,
                            String statusLabel,
                            String statusColor) {
        this.id = id;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.codeSnippet = codeSnippet;
        this.categories = categories == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(categories));
        this.status = status;
        this.statusLabel = statusLabel;
        this.statusColor = statusColor;
    }

    public String getId() {
        return id;
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

    public String getCodeSnippet() {
        return codeSnippet;
    }

    public List<CategoryFilterDTO> getCategories() {
        return categories;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusColor() {
        return statusColor;
    }
}
