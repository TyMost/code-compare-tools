package com.example.codecompare.rebuild.api.dto;

import com.example.codecompare.rebuild.stats.CategoryFilterDTO;

import java.util.Collections;
import java.util.List;

/**
 * 代码块列表条目视图。
 */
public class CodeBlockItemView {

    private final String id;
    private final String filePath;
    private final int startLine;
    private final int endLine;
    private final String codeSnippet;
    private final List<CategoryFilterDTO> categories;
    private final String status;
    private final String statusLabel;

    public CodeBlockItemView(String id,
                             String filePath,
                             int startLine,
                             int endLine,
                             String codeSnippet,
                             List<CategoryFilterDTO> categories,
                             String status,
                             String statusLabel) {
        this.id = id;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.codeSnippet = codeSnippet;
        this.categories = categories == null ? Collections.emptyList() : Collections.unmodifiableList(categories);
        this.status = status;
        this.statusLabel = statusLabel;
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
}
