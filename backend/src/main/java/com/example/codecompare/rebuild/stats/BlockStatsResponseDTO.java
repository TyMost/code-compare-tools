package com.example.codecompare.rebuild.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 代码块分页与统计响应体。
 */
public final class BlockStatsResponseDTO {

    private final List<CodeBlockItemDTO> data;
    private final int page;
    private final int size;
    private final long total;
    private final int totalPages;
    private final List<CategoryFilterDTO> categoryOptions;
    private final double newCodeRatio;
    private final long totalLines;

    public BlockStatsResponseDTO(List<CodeBlockItemDTO> data,
                                 int page,
                                 int size,
                                 long total,
                                 int totalPages,
                                 List<CategoryFilterDTO> categoryOptions,
                                 double newCodeRatio,
                                 long totalLines) {
        this.data = data == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(data));
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
        this.categoryOptions = categoryOptions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(categoryOptions));
        this.newCodeRatio = newCodeRatio;
        this.totalLines = totalLines;
    }

    public List<CodeBlockItemDTO> getData() {
        return data;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<CategoryFilterDTO> getCategoryOptions() {
        return categoryOptions;
    }

    public double getNewCodeRatio() {
        return newCodeRatio;
    }

    public long getTotalLines() {
        return totalLines;
    }
}
