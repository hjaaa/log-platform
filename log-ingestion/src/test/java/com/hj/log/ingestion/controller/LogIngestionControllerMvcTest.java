package com.hj.log.ingestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.ingestion.dto.IngestRequest;
import com.hj.log.ingestion.dto.IngestResponse;
import com.hj.log.ingestion.dto.LogEventDto;
import com.hj.log.ingestion.dto.RejectItem;
import com.hj.log.ingestion.service.LogIngestionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LogIngestionControllerMvcTest {

    @Mock private LogIngestionService service;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LogIngestionController(service)).build();
        json.findAndRegisterModules();
    }

    @Test
    void should_return_accepted_count() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestResponse(2, List.of()));

        IngestRequest req = wrap(event(), event());

        mvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accepted").value(2))
                .andExpect(jsonPath("$.data.rejected").isArray())
                .andExpect(jsonPath("$.data.rejected").isEmpty());
    }

    @Test
    void should_return_rejected_with_index_and_reason() throws Exception {
        when(service.ingest(any())).thenReturn(
                new IngestResponse(1, List.of(new RejectItem(2, "INGEST_INVALID_LEVEL"))));

        mvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(wrap(event()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(1))
                .andExpect(jsonPath("$.data.rejected[0].index").value(2))
                .andExpect(jsonPath("$.data.rejected[0].reason").value("INGEST_INVALID_LEVEL"));
    }

    @Test
    void should_400_when_events_missing() throws Exception {
        IngestRequest req = new IngestRequest(); // events == null
        mvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    private static LogEventDto event() {
        LogEventDto e = new LogEventDto();
        e.setLevel("INFO");
        e.setLogKind("application");
        e.setMessage("hello");
        e.setClientTs(Instant.now());
        return e;
    }

    private static IngestRequest wrap(LogEventDto... events) {
        IngestRequest r = new IngestRequest();
        r.setEvents(java.util.Arrays.asList(events));
        return r;
    }
}
