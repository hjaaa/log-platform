package com.hj.log.query.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hj.log.common.enums.LogKind;
import com.hj.log.common.enums.LogLevel;
import com.hj.log.query.dto.LogSearchCriteria;
import com.hj.log.query.dto.LogView;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("mapper-test")
@Import(LogQueryMapperTest.JdbcTemplateConfig.class)
class LogQueryMapperTest {

    @TestConfiguration
    static class JdbcTemplateConfig {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }
    }

    @Autowired private LogQueryMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private static final long APP_ID_ORDER = 1L;
    private static final long APP_ID_PAY = 2L;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM logs");
        jdbc.update("DELETE FROM app_registrations");
        jdbc.update(
                "INSERT INTO app_registrations (id, code, name, owner, environment, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                APP_ID_ORDER, "order-service", "Order", "team-a", "prod", "active");
        jdbc.update(
                "INSERT INTO app_registrations (id, code, name, owner, environment, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                APP_ID_PAY, "pay-service", "Pay", "team-b", "prod", "active");
    }

    @Test
    void search_should_filter_by_app_code_via_join() {
        Instant base = Instant.now().minus(10, ChronoUnit.MINUTES);
        insert(APP_ID_ORDER, LogLevel.INFO, "msg-order-1", null, null, base);
        insert(APP_ID_PAY, LogLevel.INFO, "msg-pay-1", null, null, base.plusSeconds(1));

        LogSearchCriteria c = new LogSearchCriteria();
        c.setAppCode("order-service");
        c.setFrom(base.minusSeconds(60));
        c.setTo(Instant.now());

        List<LogView> rows = mapper.search(c, null, null, 100);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getMessage()).isEqualTo("msg-order-1");
        assertThat(rows.get(0).getAppCode()).isEqualTo("order-service");
    }

    @Test
    void search_should_filter_by_multiple_levels() {
        Instant base = Instant.now().minus(5, ChronoUnit.MINUTES);
        insert(APP_ID_ORDER, LogLevel.ERROR, "err1", null, null, base);
        insert(APP_ID_ORDER, LogLevel.WARN, "warn1", null, null, base.plusSeconds(1));
        insert(APP_ID_ORDER, LogLevel.INFO, "info1", null, null, base.plusSeconds(2));

        LogSearchCriteria c = new LogSearchCriteria();
        c.setAppCode("order-service");
        c.setLevels(List.of(LogLevel.ERROR, LogLevel.WARN));
        c.setFrom(base.minusSeconds(60));
        c.setTo(Instant.now());

        List<LogView> rows = mapper.search(c, null, null, 100);
        assertThat(rows).extracting(LogView::getLevel)
                .containsExactlyInAnyOrder(LogLevel.ERROR, LogLevel.WARN);
    }

    @Test
    void search_should_filter_by_keyword_prefix() {
        Instant base = Instant.now().minus(5, ChronoUnit.MINUTES);
        insert(APP_ID_ORDER, LogLevel.INFO, "PAY-001 success", null, null, base);
        insert(APP_ID_ORDER, LogLevel.INFO, "ORDER-002 success", null, null, base.plusSeconds(1));

        LogSearchCriteria c = new LogSearchCriteria();
        c.setAppCode("order-service");
        c.setKeyword("PAY-");
        c.setFrom(base.minusSeconds(60));
        c.setTo(Instant.now());

        List<LogView> rows = mapper.search(c, null, null, 100);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getMessage()).startsWith("PAY-");
    }

    @Test
    void search_should_paginate_via_cursor_without_duplicates() {
        Instant base = Instant.now().minus(20, ChronoUnit.MINUTES);
        for (int i = 0; i < 7; i++) {
            insert(APP_ID_ORDER, LogLevel.INFO, "m-" + i, null, null,
                    base.plusSeconds(i));
        }

        LogSearchCriteria c = new LogSearchCriteria();
        c.setAppCode("order-service");
        c.setFrom(base.minusSeconds(60));
        c.setTo(Instant.now());

        // 第一页：取前 3 条（按 server_ts DESC, id DESC，最新的在前）
        List<LogView> page1 = mapper.search(c, null, null, 3);
        assertThat(page1).hasSize(3);
        assertThat(page1).extracting(LogView::getMessage)
                .containsExactly("m-6", "m-5", "m-4");

        // 第二页：用第一页最后一条作 cursor
        LogView lastP1 = page1.get(2);
        List<LogView> page2 = mapper.search(c, lastP1.getServerTs(), lastP1.getId(), 3);
        assertThat(page2).extracting(LogView::getMessage)
                .containsExactly("m-3", "m-2", "m-1");

        // 第三页：用第二页最后一条作 cursor，应剩 1 条
        LogView lastP2 = page2.get(2);
        List<LogView> page3 = mapper.search(c, lastP2.getServerTs(), lastP2.getId(), 3);
        assertThat(page3).extracting(LogView::getMessage).containsExactly("m-0");
    }

    @Test
    void search_should_filter_by_request_id() {
        Instant base = Instant.now().minus(5, ChronoUnit.MINUTES);
        insert(APP_ID_ORDER, LogLevel.INFO, "a", null, "req-1", base);
        insert(APP_ID_ORDER, LogLevel.INFO, "b", null, "req-2", base.plusSeconds(1));

        LogSearchCriteria c = new LogSearchCriteria();
        c.setAppCode("order-service");
        c.setRequestId("req-2");
        c.setFrom(base.minusSeconds(60));
        c.setTo(Instant.now());

        List<LogView> rows = mapper.search(c, null, null, 100);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRequestId()).isEqualTo("req-2");
    }

    @Test
    void find_by_id_should_return_null_when_missing() {
        assertThat(mapper.findById(99999L)).isNull();
    }

    @Test
    void find_by_id_should_populate_app_code() {
        Instant now = Instant.now();
        insert(APP_ID_ORDER, LogLevel.INFO, "hello", null, null, now);
        Long id = jdbc.queryForObject(
                "SELECT id FROM logs WHERE message='hello'", Long.class);

        LogView v = mapper.findById(id);
        assertThat(v).isNotNull();
        assertThat(v.getMessage()).isEqualTo("hello");
        assertThat(v.getAppCode()).isEqualTo("order-service");
    }

    @Test
    void search_by_trace_should_order_by_server_ts_asc() {
        Instant base = Instant.now().minus(10, ChronoUnit.MINUTES);
        insert(APP_ID_ORDER, LogLevel.INFO, "trace-step-3", "tr-1", null, base.plusSeconds(2));
        insert(APP_ID_ORDER, LogLevel.INFO, "trace-step-1", "tr-1", null, base);
        insert(APP_ID_ORDER, LogLevel.INFO, "trace-step-2", "tr-1", null, base.plusSeconds(1));
        insert(APP_ID_ORDER, LogLevel.INFO, "other-trace", "tr-9", null, base.plusSeconds(3));

        List<LogView> rows = mapper.searchByTrace("tr-1", 10);
        assertThat(rows).extracting(LogView::getMessage)
                .containsExactly("trace-step-1", "trace-step-2", "trace-step-3");
    }

    @Test
    void search_by_trace_should_respect_limit() {
        Instant base = Instant.now().minus(10, ChronoUnit.MINUTES);
        for (int i = 0; i < 5; i++) {
            insert(APP_ID_ORDER, LogLevel.INFO, "t-" + i, "tr-many", null,
                    base.plusSeconds(i));
        }
        List<LogView> rows = mapper.searchByTrace("tr-many", 3);
        assertThat(rows).hasSize(3);
    }

    private void insert(long appId, LogLevel level, String message,
                        String traceId, String requestId, Instant serverTs) {
        jdbc.update(
                "INSERT INTO logs (app_id, log_kind, level, message, trace_id, request_id, "
                        + "client_ts, server_ts) VALUES (?,?,?,?,?,?,?,?)",
                appId,
                LogKind.application.name(),
                level.name(),
                message,
                traceId,
                requestId,
                java.sql.Timestamp.from(serverTs),
                java.sql.Timestamp.from(serverTs));
    }
}
