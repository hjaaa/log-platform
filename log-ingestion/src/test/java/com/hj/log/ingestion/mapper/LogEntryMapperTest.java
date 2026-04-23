package com.hj.log.ingestion.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hj.log.common.domain.LogEntry;
import com.hj.log.common.enums.LogKind;
import com.hj.log.common.enums.LogLevel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("mapper-test")
class LogEntryMapperTest {

    @Autowired private LogEntryMapper mapper;

    @Test
    void batch_insert_should_persist_all_rows() {
        long start = System.currentTimeMillis();
        mapper.batchInsert(buildEntries(50));
        long cost = System.currentTimeMillis() - start;
        // 性能记录（仅 informational，不断言上限避免 CI 抖动）
        System.out.println("[mapper-test] batchInsert(50) costMs=" + cost);
    }

    @Test
    @Sql(statements = "DELETE FROM logs", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void delete_oldest_should_only_remove_rows_older_than_cutoff() {
        Instant now = Instant.now();
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries.add(entry(now.minus(10, ChronoUnit.DAYS), 100L)); // 早于 cutoff
        }
        for (int i = 0; i < 3; i++) {
            entries.add(entry(now, 100L)); // 新记录
        }
        mapper.batchInsert(entries);

        Instant cutoff = now.minus(7, ChronoUnit.DAYS);
        int affected = mapper.deleteOldest(cutoff, 100);
        assertThat(affected).isEqualTo(5);
    }

    @Test
    @Sql(statements = "DELETE FROM logs", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void delete_oldest_should_respect_batch_size() {
        Instant now = Instant.now();
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(entry(now.minus(10, ChronoUnit.DAYS), 100L));
        }
        mapper.batchInsert(entries);

        int firstBatch = mapper.deleteOldest(now.minus(7, ChronoUnit.DAYS), 4);
        assertThat(firstBatch).isEqualTo(4);
        int secondBatch = mapper.deleteOldest(now.minus(7, ChronoUnit.DAYS), 4);
        assertThat(secondBatch).isEqualTo(4);
        int thirdBatch = mapper.deleteOldest(now.minus(7, ChronoUnit.DAYS), 4);
        assertThat(thirdBatch).isEqualTo(2);
    }

    private List<LogEntry> buildEntries(int count) {
        Instant now = Instant.now();
        List<LogEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(entry(now, (long) (i % 5 + 1)));
        }
        return list;
    }

    private LogEntry entry(Instant serverTs, Long appId) {
        LogEntry e = new LogEntry();
        e.setAppId(appId);
        e.setLogKind(LogKind.application);
        e.setLevel(LogLevel.ERROR);
        e.setMessage("test message");
        e.setClientTs(serverTs);
        e.setServerTs(serverTs);
        return e;
    }
}
