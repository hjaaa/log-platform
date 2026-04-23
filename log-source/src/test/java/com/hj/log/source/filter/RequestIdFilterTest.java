package com.hj.log.source.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clean() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Test
    void should_generate_uuid_when_header_missing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/logs");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (r, s) -> {
            String inFlight = MDC.get(RequestIdFilter.MDC_KEY);
            assertThat(inFlight).isNotBlank().hasSize(36);
        };

        filter.doFilter(req, resp, chain);

        assertThat(resp.getHeader(RequestIdFilter.HEADER_NAME)).isNotBlank();
        // 请求结束后 MDC 应被清理
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void should_use_header_when_provided() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        req.addHeader(RequestIdFilter.HEADER_NAME, "req-from-client-001");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (r, s) -> {
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("req-from-client-001");
        };

        filter.doFilter(req, resp, chain);

        assertThat(resp.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("req-from-client-001");
    }

    @Test
    void should_clear_mdc_even_when_chain_throws() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/logs");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (r, s) -> {
            throw new RuntimeException("downstream boom");
        };

        try {
            filter.doFilter(req, resp, chain);
        } catch (Exception ignored) {
            // 期望异常
        }
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
