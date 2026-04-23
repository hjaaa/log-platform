package com.hj.log.ingestion.dto;

/** 部分失败明细：{@code index} = events 数组下标；{@code reason} = ErrorCode 字符串。 */
public class RejectItem {

    private int index;
    private String reason;

    public RejectItem() {
    }

    public RejectItem(int index, String reason) {
        this.index = index;
        this.reason = reason;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
