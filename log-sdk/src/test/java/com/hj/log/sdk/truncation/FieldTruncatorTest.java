package com.hj.log.sdk.truncation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hj.log.sdk.TestEvents;
import com.hj.log.sdk.model.LogEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FieldTruncatorTest {

    private final AtomicInteger count = new AtomicInteger();
    private final FieldTruncator.TruncationCounter counter = count::incrementAndGet;

    @Test
    void should_leave_short_message_untouched() {
        FieldTruncator t = new FieldTruncator(100, 200, counter);
        LogEvent e = TestEvents.of("hello");
        assertThat(t.apply(e).message()).isEqualTo("hello");
        assertThat(count.get()).isZero();
    }

    @Test
    void should_truncate_oversize_message_with_suffix() {
        FieldTruncator t = new FieldTruncator(50, 100, counter);
        String raw = "x".repeat(80);
        LogEvent truncated = t.apply(TestEvents.of(raw));
        assertThat(truncated.message()).hasSize(50).endsWith(FieldTruncator.TRUNCATED_SUFFIX);
        assertThat(truncated.message().substring(0, 50 - FieldTruncator.SUFFIX_LENGTH))
                .isEqualTo("x".repeat(50 - FieldTruncator.SUFFIX_LENGTH));
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void should_truncate_message_and_stack_trace_both() {
        FieldTruncator t = new FieldTruncator(30, 40, counter);
        LogEvent input =
                TestEvents.of("a".repeat(100))
                        .withStackTrace("b".repeat(200));
        LogEvent out = t.apply(input);
        assertThat(out.message()).hasSize(30).endsWith(FieldTruncator.TRUNCATED_SUFFIX);
        assertThat(out.stackTrace()).hasSize(40).endsWith(FieldTruncator.TRUNCATED_SUFFIX);
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void should_leave_null_fields_untouched() {
        FieldTruncator t = new FieldTruncator(30, 40, counter);
        LogEvent e =
                new LogEvent(
                        "application",
                        "ERROR",
                        "m",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        assertThat(t.apply(e)).isEqualTo(e);
    }

    @Test
    void should_reject_too_small_limit() {
        assertThatThrownBy(() -> new FieldTruncator(5, 100, counter))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldTruncator(100, 5, counter))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
