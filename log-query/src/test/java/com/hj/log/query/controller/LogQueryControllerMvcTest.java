package com.hj.log.query.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hj.log.common.base.ResponseResult;
import com.hj.log.common.enums.LogKind;
import com.hj.log.common.enums.LogLevel;
import com.hj.log.common.exception.BusinessException;
import com.hj.log.query.config.QueryProperties;
import com.hj.log.query.cursor.CursorCodec;
import com.hj.log.query.dto.LogView;
import com.hj.log.query.mapper.LogQueryMapper;
import com.hj.log.query.service.LogQueryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller MockMvc 测试。使用真实 {@link LogQueryService}（覆盖校验路径）+ 桩 Mapper。
 * GlobalExceptionHandler 由 F-08 (log-web) 提供；这里用一个本地等价的 advice 让 BusinessException
 * 也能映射成 ResponseResult.fail。
 */
@ExtendWith(MockitoExtension.class)
class LogQueryControllerMvcTest {

    @Mock private LogQueryMapper mapper;

    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        QueryProperties props = new QueryProperties();
        LogQueryService service = new LogQueryService(mapper, props);
        LogQueryController controller = new LogQueryController(service, objectMapper);

        MappingJackson2HttpMessageConverter conv = new MappingJackson2HttpMessageConverter(objectMapper);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(conv)
                .setControllerAdvice(new BusinessExceptionAdvice())
                .build();
    }

    @Test
    void search_should_return_items_and_next_cursor_when_more_rows_available() throws Exception {
        // 准备 4 条数据；service 会 fetch limit+1=3 条，触发 hasMore 并裁剪到 2 条
        List<LogView> rows = new ArrayList<>();
        rows.add(view(101L, "m1", Instant.parse("2026-04-23T11:00:02Z")));
        rows.add(view(100L, "m2", Instant.parse("2026-04-23T11:00:01Z")));
        rows.add(view(99L, "m3", Instant.parse("2026-04-23T11:00:00Z")));
        when(mapper.search(any(), any(), any(), eq(3))).thenReturn(rows);

        mvc.perform(get("/api/v1/logs/search")
                        .param("appCode", "order-service")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].message").value("m1"))
                .andExpect(jsonPath("$.data.items[1].message").value("m2"))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.nextCursor").value(
                        CursorCodec.encode(Instant.parse("2026-04-23T11:00:01Z").toEpochMilli(), 100L)));
    }

    @Test
    void search_should_omit_next_cursor_when_no_more_rows() throws Exception {
        when(mapper.search(any(), any(), any(), anyInt()))
                .thenReturn(List.of(view(1L, "only", Instant.parse("2026-04-23T11:00:00Z"))));

        mvc.perform(get("/api/v1/logs/search").param("appCode", "order-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void search_should_400_when_neither_app_code_nor_trace_id() throws Exception {
        mvc.perform(get("/api/v1/logs/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_MISSING_APP_FILTER"));
    }

    @Test
    void search_should_400_when_keyword_starts_with_percent() throws Exception {
        mvc.perform(get("/api/v1/logs/search")
                        .param("appCode", "order-service")
                        .param("keyword", "%inject"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_KEYWORD_PATTERN_INVALID"));
    }

    @Test
    void search_should_400_when_page_size_too_large() throws Exception {
        mvc.perform(get("/api/v1/logs/search")
                        .param("appCode", "order-service")
                        .param("pageSize", "9999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_PAGE_SIZE_TOO_LARGE"));
    }

    @Test
    void search_should_400_when_cursor_invalid() throws Exception {
        mvc.perform(get("/api/v1/logs/search")
                        .param("appCode", "order-service")
                        .param("cursor", "!!!notbase64!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_INVALID_CURSOR"));
    }

    @Test
    void search_should_crop_fields_when_fields_param_provided() throws Exception {
        when(mapper.search(any(), any(), any(), anyInt()))
                .thenReturn(List.of(view(1L, "hello", Instant.parse("2026-04-23T11:00:00Z"))));

        mvc.perform(get("/api/v1/logs/search")
                        .param("appCode", "order-service")
                        .param("fields", "id,level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].level").value("INFO"))
                .andExpect(jsonPath("$.data.items[0].message").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].appCode").doesNotExist());
    }

    @Test
    void search_should_pass_multi_level_filter() throws Exception {
        when(mapper.search(any(), any(), any(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/v1/logs/search")
                        .param("appCode", "order-service")
                        .param("level", "ERROR,WARN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        // 只验证不报错；实际 levels 透传到 mapper 已由 mapper 集成测试保证
    }

    @Test
    void get_by_id_should_return_404_when_missing() throws Exception {
        when(mapper.findById(anyLong())).thenReturn(null);

        mvc.perform(get("/api/v1/logs/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUERY_LOG_NOT_FOUND"));
    }

    @Test
    void get_by_id_should_return_view_when_present() throws Exception {
        when(mapper.findById(123L)).thenReturn(view(123L, "abc", Instant.parse("2026-04-23T11:00:00Z")));

        mvc.perform(get("/api/v1/logs/{id}", 123))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(123))
                .andExpect(jsonPath("$.data.message").value("abc"));
    }

    @Test
    void trace_should_set_truncated_true_when_more_than_limit() throws Exception {
        // 请求 limit=2，service fetch limit+1=3；mapper 返回 3 条 → truncated=true
        when(mapper.searchByTrace(eq("tr-1"), eq(3))).thenReturn(List.of(
                view(1L, "a", Instant.parse("2026-04-23T11:00:00Z")),
                view(2L, "b", Instant.parse("2026-04-23T11:00:01Z")),
                view(3L, "c", Instant.parse("2026-04-23T11:00:02Z"))));

        mvc.perform(get("/api/v1/logs/trace/{tid}", "tr-1").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void trace_should_set_truncated_false_when_within_limit() throws Exception {
        when(mapper.searchByTrace(eq("tr-2"), anyInt())).thenReturn(List.of(
                view(1L, "a", Instant.parse("2026-04-23T11:00:00Z"))));

        mvc.perform(get("/api/v1/logs/trace/{tid}", "tr-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void trace_should_400_when_limit_exceeds_max() throws Exception {
        mvc.perform(get("/api/v1/logs/trace/{tid}", "tr-x").param("limit", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_LIMIT_TOO_LARGE"));
    }

    private static LogView view(long id, String message, Instant serverTs) {
        LogView v = new LogView();
        v.setId(id);
        v.setAppId(1L);
        v.setAppCode("order-service");
        v.setLogKind(LogKind.application);
        v.setLevel(LogLevel.INFO);
        v.setMessage(message);
        v.setClientTs(serverTs);
        v.setServerTs(serverTs);
        return v;
    }

    @RestControllerAdvice
    static class BusinessExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<ResponseResult<Void>> handle(BusinessException ex) {
            return ResponseEntity.status(ex.getHttpStatus())
                    .body(ResponseResult.fail(ex.getCode(), ex.getMessage()));
        }
    }
}
