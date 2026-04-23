package com.hj.log.query.dto;

import java.util.List;

/** 通用游标分页结构（{@code /search} 使用）。 */
public class Page<T> {

    private List<T> items;
    private String nextCursor;
    private int pageSize;

    public Page() {
    }

    public Page(List<T> items, String nextCursor, int pageSize) {
        this.items = items;
        this.nextCursor = nextCursor;
        this.pageSize = pageSize;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
