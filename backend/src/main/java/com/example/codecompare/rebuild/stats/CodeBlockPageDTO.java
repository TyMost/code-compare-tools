package com.example.codecompare.rebuild.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 代码块分页返回 DTO。
 */
public final class CodeBlockPageDTO {

    private final List<CodeBlockItemDTO> data;
    private final int page;
    private final int size;
    private final long total;
    private final int totalPages;

    public CodeBlockPageDTO(List<CodeBlockItemDTO> data, int page, int size, long total, int totalPages) {
        this.data = data == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(data));
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
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
}
