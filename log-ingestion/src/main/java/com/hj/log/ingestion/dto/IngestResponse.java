package com.hj.log.ingestion.dto;

import java.util.List;

/** {@code POST /api/v1/logs} 响应。整批 200，部分失败由 {@link #rejected} 列出。 */
public class IngestResponse {

    private int accepted;
    private List<RejectItem> rejected;

    public IngestResponse() {
    }

    public IngestResponse(int accepted, List<RejectItem> rejected) {
        this.accepted = accepted;
        this.rejected = rejected;
    }

    public int getAccepted() {
        return accepted;
    }

    public void setAccepted(int accepted) {
        this.accepted = accepted;
    }

    public List<RejectItem> getRejected() {
        return rejected;
    }

    public void setRejected(List<RejectItem> rejected) {
        this.rejected = rejected;
    }
}
