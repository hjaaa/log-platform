package com.hj.log.ingestion.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hj.log.ingestion.config.RetentionProperties;
import com.hj.log.ingestion.mapper.LogEntryMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogRetentionTaskTest {

    @Mock private LogEntryMapper mapper;
    private RetentionProperties props;
    private LogRetentionTask task;

    @BeforeEach
    void setUp() {
        props = new RetentionProperties();
        props.setBatchSize(500);
        props.setDays(7);
        props.setBatchPauseMs(0L); // 测试中不暂停
        task = new LogRetentionTask(mapper, props);
    }

    @Test
    void should_loop_until_affected_below_batch_size() {
        // 模拟：第 1/2 批均删满 500，第 3 批仅删 200 → 退出
        when(mapper.deleteOldest(any(Instant.class), eq(500))).thenReturn(500, 500, 200);

        int total = task.runOnce();

        assertThat(total).isEqualTo(1200);
        verify(mapper, times(3)).deleteOldest(any(Instant.class), eq(500));
    }

    @Test
    void should_exit_immediately_when_first_batch_below_size() {
        when(mapper.deleteOldest(any(Instant.class), eq(500))).thenReturn(120);
        int total = task.runOnce();
        assertThat(total).isEqualTo(120);
        verify(mapper, times(1)).deleteOldest(any(Instant.class), eq(500));
    }

    @Test
    void should_exit_when_no_data_to_delete() {
        when(mapper.deleteOldest(any(Instant.class), eq(500))).thenReturn(0);
        int total = task.runOnce();
        assertThat(total).isZero();
        verify(mapper, times(1)).deleteOldest(any(Instant.class), eq(500));
    }

    @Test
    void should_break_loop_when_mapper_throws() {
        when(mapper.deleteOldest(any(Instant.class), eq(500)))
                .thenReturn(500)
                .thenThrow(new RuntimeException("DB down"));
        int total = task.runOnce();
        assertThat(total).isEqualTo(500);
        verify(mapper, times(2)).deleteOldest(any(Instant.class), eq(500));
    }

    @Test
    void cutoff_should_be_now_minus_days() {
        when(mapper.deleteOldest(any(Instant.class), eq(500))).thenAnswer(inv -> {
            Instant cutoff = inv.getArgument(0);
            // cutoff 应在过去 7 天内（≈ 7 * 86400_000 ms）
            long diffSec = Instant.now().getEpochSecond() - cutoff.getEpochSecond();
            assertThat(diffSec).isBetween(7L * 86400 - 60, 7L * 86400 + 60);
            return 0;
        });
        task.runOnce();
    }
}
