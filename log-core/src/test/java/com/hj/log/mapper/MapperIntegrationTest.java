package com.hj.log.mapper;

import com.hj.log.domain.AlertRule;
import com.hj.log.domain.LogEntry;
import com.hj.log.domain.LogSource;
import com.hj.log.domain.NotificationConfig;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@Sql("classpath:db/schema.sql")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:logdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mybatis.mapper-locations=classpath*:mapper/*.xml"
})
class MapperIntegrationTest {

    @Autowired
    private LogEntryMapper logEntryMapper;

    @Autowired
    private AlertRuleMapper alertRuleMapper;

    @Autowired
    private NotificationConfigMapper notificationConfigMapper;

    @Autowired
    private LogSourceMapper logSourceMapper;

    @Test
    void should_insert_and_select_log_entry() {
        String id = UUID.randomUUID().toString().replace("-", "");
        LogEntry entry = new LogEntry();
        entry.setId(id);
        entry.setServiceName("log-web");
        entry.setLogLevel("ERROR");
        entry.setTimestamp(LocalDateTime.of(2026, 2, 6, 10, 0));
        entry.setMessage("error message");
        entry.setLoggerName("com.hj.log.Test");
        entry.setThreadName("main");
        entry.setStackTrace("stack");
        entry.setContextData("{\"trace\":\"t-1\"}");
        entry.setSourceIp("127.0.0.1");
        entry.setTraceId("t-1");

        int inserted = logEntryMapper.insert(entry);
        LogEntry queried = logEntryMapper.selectById(id);

        assertThat(inserted).isEqualTo(1);
        assertThat(queried).isNotNull();
        assertThat(queried.getServiceName()).isEqualTo("log-web");
        assertThat(normalizeJsonValue(queried.getContextData())).isEqualTo("{\"trace\":\"t-1\"}");
    }

    @Test
    void should_insert_and_select_alert_rule() {
        String id = UUID.randomUUID().toString().replace("-", "");
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setRuleName("error rule");
        rule.setServiceName("log-web");
        rule.setLogLevel("ERROR");
        rule.setRegexPattern(".*Exception.*");
        rule.setThresholdCount(10);
        rule.setTimeWindow(5);
        rule.setCoolDownPeriod(10);
        rule.setEnabled(1);
        rule.setNotificationChannels("[\"channel-1\"]");

        int inserted = alertRuleMapper.insert(rule);
        AlertRule queried = alertRuleMapper.selectById(id);

        assertThat(inserted).isEqualTo(1);
        assertThat(queried).isNotNull();
        assertThat(queried.getRuleName()).isEqualTo("error rule");
        assertThat(normalizeJsonValue(queried.getNotificationChannels())).isEqualTo("[\"channel-1\"]");
    }

    @Test
    void should_insert_and_select_notification_config() {
        String id = UUID.randomUUID().toString().replace("-", "");
        NotificationConfig config = new NotificationConfig();
        config.setId(id);
        config.setConfigName("ding config");
        config.setChannelType("DINGTALK");
        config.setConfigData("{\"webhook\":\"https://example\"}");
        config.setEnabled(1);

        int inserted = notificationConfigMapper.insert(config);
        NotificationConfig queried = notificationConfigMapper.selectById(id);

        assertThat(inserted).isEqualTo(1);
        assertThat(queried).isNotNull();
        assertThat(queried.getChannelType()).isEqualTo("DINGTALK");
        assertThat(normalizeJsonValue(queried.getConfigData())).isEqualTo("{\"webhook\":\"https://example\"}");
    }

    @Test
    void should_insert_and_select_log_source() {
        String id = UUID.randomUUID().toString().replace("-", "");
        LogSource source = new LogSource();
        source.setId(id);
        source.setSourceName("source-a");
        source.setServiceName("log-web");
        source.setSourceType("HTTP_PUSH");
        source.setAuthToken("token-1");
        source.setStatus("ENABLED");

        int inserted = logSourceMapper.insert(source);
        LogSource queried = logSourceMapper.selectById(id);

        assertThat(inserted).isEqualTo(1);
        assertThat(queried).isNotNull();
        assertThat(queried.getSourceName()).isEqualTo("source-a");
        assertThat(queried.getSourceType()).isEqualTo("HTTP_PUSH");
    }

    private String normalizeJsonValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"");
        }
        return value;
    }
}
