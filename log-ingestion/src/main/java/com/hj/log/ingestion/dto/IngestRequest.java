package com.hj.log.ingestion.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** {@code POST /api/v1/logs} 入参。批 size 上限由 service 按配置项校验。 */
public class IngestRequest {

    @NotEmpty
    private List<LogEventDto> events;

    public List<LogEventDto> getEvents() {
        return events;
    }

    public void setEvents(List<LogEventDto> events) {
        this.events = events;
    }
}
