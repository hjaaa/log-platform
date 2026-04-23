package com.hj.log.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hj.log.common.context.RequestContextWriter;
import com.hj.log.common.enums.Scope;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.ingestion.config.IngestionProperties;
import com.hj.log.ingestion.dto.IngestRequest;
import com.hj.log.ingestion.dto.IngestResponse;
import com.hj.log.ingestion.dto.LogEventDto;
import com.hj.log.ingestion.mapper.LogEntryMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogIngestionServiceTest {

    @Mock private LogEntryMapper mapper;
    private IngestionProperties props;
    private LogIngestionService service;

    @BeforeEach
    void setUp() {
        props = new IngestionProperties();
        // 使用紧凑阈值便于触发边界
        props.setMaxBatchSize(3);
        props.setMaxMessageLength(50);
        props.setMaxStackTraceLength(80);
        service = new LogIngestionService(mapper, props);
        RequestContextWriter.set(99L, Scope.WRITE, 7L, "req-test");
        lenient().when(mapper.batchInsert(any())).thenReturn(0); // 返回值不参与逻辑
    }

    @AfterEach
    void clean() {
        RequestContextWriter.clear();
    }

    @Test
    void should_accept_all_events_when_valid() {
        IngestRequest req = wrap(event("INFO", "application", "hello"), event("ERROR", "application", "boom"));

        IngestResponse resp = service.ingest(req);

        assertThat(resp.getAccepted()).isEqualTo(2);
        assertThat(resp.getRejected()).isEmpty();
        verify(mapper, times(1)).batchInsert(any());
    }

    @Test
    void should_throw_INGEST_BATCH_TOO_LARGE_when_exceeding_limit() {
        IngestRequest req = wrap(
                event("INFO", "application", "1"),
                event("INFO", "application", "2"),
                event("INFO", "application", "3"),
                event("INFO", "application", "4"));

        assertThatThrownBy(() -> service.ingest(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INGEST_BATCH_TOO_LARGE);
        verify(mapper, never()).batchInsert(any());
    }

    @Test
    void should_collect_per_event_failures_in_rejected_list() {
        IngestRequest req = wrap(
                event("INFO", "application", "ok"),                          // pass
                event("INFO", "application", "  "),                          // INGEST_MESSAGE_BLANK
                event("FATAL", "application", "bad-level"),                  // INGEST_INVALID_LEVEL
                event("INFO", "biz", "bad-kind"));                           // INGEST_INVALID_KIND（先 batch-size 调大）
        // 调大 batch 上限以容纳 4 条
        props.setMaxBatchSize(10);

        IngestResponse resp = service.ingest(req);

        assertThat(resp.getAccepted()).isEqualTo(1);
        assertThat(resp.getRejected()).extracting("index", "reason")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, ErrorCode.INGEST_MESSAGE_BLANK),
                        org.assertj.core.groups.Tuple.tuple(2, ErrorCode.INGEST_INVALID_LEVEL),
                        org.assertj.core.groups.Tuple.tuple(3, ErrorCode.INGEST_INVALID_KIND));
    }

    @Test
    void should_reject_message_too_long() {
        String tooLong = "x".repeat(props.getMaxMessageLength() + 1);
        IngestResponse resp = service.ingest(wrap(event("INFO", "application", tooLong)));

        assertThat(resp.getAccepted()).isZero();
        assertThat(resp.getRejected()).hasSize(1);
        assertThat(resp.getRejected().get(0).getReason()).isEqualTo(ErrorCode.INGEST_MESSAGE_TOO_LONG);
    }

    @Test
    void should_reject_stack_trace_too_long() {
        LogEventDto e = event("ERROR", "application", "boom");
        e.setStackTrace("y".repeat(props.getMaxStackTraceLength() + 1));
        IngestResponse resp = service.ingest(wrap(e));

        assertThat(resp.getAccepted()).isZero();
        assertThat(resp.getRejected().get(0).getReason()).isEqualTo(ErrorCode.INGEST_MESSAGE_TOO_LONG);
    }

    @Test
    void should_throw_INTERNAL_ERROR_when_request_context_missing() {
        RequestContextWriter.clear();
        assertThatThrownBy(() -> service.ingest(wrap(event("INFO", "application", "hi"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void should_default_client_ts_to_server_ts_when_missing() {
        LogEventDto e = event("INFO", "application", "hi");
        e.setClientTs(null);
        IngestResponse resp = service.ingest(wrap(e));
        assertThat(resp.getAccepted()).isEqualTo(1);
    }

    private static LogEventDto event(String level, String kind, String msg) {
        LogEventDto e = new LogEventDto();
        e.setLevel(level);
        e.setLogKind(kind);
        e.setMessage(msg);
        e.setClientTs(Instant.now());
        return e;
    }

    private static IngestRequest wrap(LogEventDto... events) {
        IngestRequest r = new IngestRequest();
        List<LogEventDto> list = new ArrayList<>();
        for (LogEventDto e : events) {
            list.add(e);
        }
        r.setEvents(list);
        return r;
    }
}
