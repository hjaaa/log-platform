package com.hj.log.common.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hj.log.common.enums.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RequestContextTest {

    @AfterEach
    void cleanUp() {
        RequestContextWriter.clear();
    }

    @Test
    void current_should_return_null_when_unset() {
        assertNull(RequestContext.current());
    }

    @Test
    void writer_should_set_then_clear() {
        RequestContextWriter.set(42L, Scope.WRITE, 7L, "req-001");

        RequestContext ctx = RequestContext.current();
        assertEquals(42L, ctx.getAppId());
        assertEquals(Scope.WRITE, ctx.getScope());
        assertEquals(7L, ctx.getKeyId());
        assertEquals("req-001", ctx.getRequestId());

        RequestContextWriter.clear();
        assertNull(RequestContext.current());
    }

    @Test
    void context_should_be_thread_local() throws Exception {
        RequestContextWriter.set(1L, Scope.READ, 2L, "main-req");

        Thread other = new Thread(() -> {
            // 兄弟线程未注入，应当看不到主线程的上下文
            if (RequestContext.current() != null) {
                throw new IllegalStateException("ThreadLocal leaked");
            }
        });
        other.start();
        other.join();

        // 主线程上下文不受影响
        assertEquals(1L, RequestContext.current().getAppId());
    }
}
