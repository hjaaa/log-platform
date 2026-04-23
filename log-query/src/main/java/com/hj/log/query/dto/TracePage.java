package com.hj.log.query.dto;

import java.util.List;

/** {@code /trace/{traceId}} 返回结构：固定上限的非分页结果，{@code truncated} 表示是否被截断。 */
public class TracePage<T> {

    private List<T> items;
    private boolean truncated;

    public TracePage() {
    }

    public TracePage(List<T> items, boolean truncated) {
        this.items = items;
        this.truncated = truncated;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
