package com.example.codecompare.rebuild.repository.model;

/**
 * 简化版的分页请求参数。
 */
public final class PageRequest {

    private final int pageIndex;
    private final int pageSize;

    private PageRequest(int pageIndex, int pageSize) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be >= 0");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
    }

    public static PageRequest of(int pageIndex, int pageSize) {
        return new PageRequest(pageIndex, pageSize);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getOffset() {
        return pageIndex * pageSize;
    }
}
