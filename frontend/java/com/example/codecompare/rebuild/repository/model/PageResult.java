package com.example.codecompare.rebuild.repository.model;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果包装类。
 *
 * @param <T> 数据类型
 */
public final class PageResult<T> {

    private final List<T> items;
    private final long total;
    private final int pageIndex;
    private final int pageSize;

    private PageResult(List<T> items, long total, int pageIndex, int pageSize) {
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
        this.total = Math.max(0, total);
        this.pageIndex = Math.max(0, pageIndex);
        this.pageSize = Math.max(1, pageSize);
    }

    public static <T> PageResult<T> empty(PageRequest request) {
        return new PageResult<>(Collections.emptyList(), 0, request.getPageIndex(), request.getPageSize());
    }

    public static <T> PageResult<T> of(List<T> items, long total, PageRequest request) {
        return new PageResult<>(items, total, request.getPageIndex(), request.getPageSize());
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getPageSize() {
        return pageSize;
    }

    public boolean hasNext() {
        return (long) (pageIndex + 1) * pageSize < total;
    }
}
