package com.hj.log.source.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.common.context.RequestContext;
import com.hj.log.common.context.RequestContextWriter;
import com.hj.log.common.domain.ApiKey;
import com.hj.log.common.enums.KeyStatus;
import com.hj.log.common.enums.Scope;
import com.hj.log.common.exception.ErrorCode;
import com.hj.log.source.config.AdminTokenProperties;
import com.hj.log.source.mapper.ApiKeyMapper;
import com.hj.log.source.service.LastUsedTracker;
import com.hj.log.source.util.HashUtil;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    private static final String ADMIN_TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock private ApiKeyMapper apiKeyMapper;
    @Mock private LastUsedTracker lastUsedTracker;

    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        AdminTokenProperties admin = new AdminTokenProperties();
        admin.setToken(ADMIN_TOKEN);
        filter = new AuthFilter(admin, apiKeyMapper, lastUsedTracker, new ObjectMapper());
    }

    @AfterEach
    void clearCtx() {
        RequestContextWriter.clear();
    }

    @Test
    void should_pass_through_health_endpoint() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isTrue();
        verify(apiKeyMapper, never()).findByHash(anyString());
    }

    @Test
    void should_pass_through_unmanaged_path() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/whatever");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isTrue();
        verify(apiKeyMapper, never()).findByHash(anyString());
    }

    @Test
    void should_reject_when_authorization_missing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_MISSING_TOKEN);
    }

    @Test
    void should_reject_when_bearer_value_blank() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        req.addHeader("Authorization", "Bearer  ");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_MISSING_TOKEN);
    }

    @Test
    void should_accept_admin_token_then_set_context() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/apps");
        req.addHeader("Authorization", "Bearer " + ADMIN_TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<Scope> scopeInChain = new AtomicReference<>();
        FilterChain chain = (r, s) -> scopeInChain.set(RequestContext.current().getScope());

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(scopeInChain.get()).isEqualTo(Scope.ADMIN);
        // 退出后清理
        assertThat(RequestContext.current()).isNull();
    }

    @Test
    void should_reject_wrong_admin_token() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/apps");
        req.addHeader("Authorization", "Bearer not-the-admin-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_INVALID_ADMIN_TOKEN);
    }

    @Test
    void should_reject_when_api_key_not_found() throws Exception {
        when(apiKeyMapper.findByHash(anyString())).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        req.addHeader("Authorization", "Bearer lpk_unknown_key");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_INVALID_KEY);
        verify(lastUsedTracker, never()).touch(anyString(), anyLong());
    }

    @Test
    void should_reject_revoked_key() throws Exception {
        ApiKey revoked = sampleKey(Scope.WRITE, KeyStatus.revoked, null);
        when(apiKeyMapper.findByHash(anyString())).thenReturn(revoked);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        req.addHeader("Authorization", "Bearer lpk_AB12CD34");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_INVALID_KEY);
        verify(lastUsedTracker, never()).touch(anyString(), anyLong());
    }

    @Test
    void should_reject_expired_key() throws Exception {
        ApiKey expired = sampleKey(Scope.WRITE, KeyStatus.active, Instant.now().minusSeconds(60));
        when(apiKeyMapper.findByHash(anyString())).thenReturn(expired);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        req.addHeader("Authorization", "Bearer lpk_AB12CD34");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_INVALID_KEY);
    }

    @Test
    void should_reject_on_scope_mismatch() throws Exception {
        ApiKey readKey = sampleKey(Scope.READ, KeyStatus.active, null);
        when(apiKeyMapper.findByHash(anyString())).thenReturn(readKey);
        // POST /logs 需要 WRITE，但提供的 key 是 READ
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        req.addHeader("Authorization", "Bearer lpk_AB12CD34");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_SCOPE_MISMATCH);
    }

    @Test
    void should_accept_valid_write_key_and_touch_last_used() throws Exception {
        ApiKey writeKey = sampleKey(Scope.WRITE, KeyStatus.active, null);
        when(apiKeyMapper.findByHash(anyString())).thenReturn(writeKey);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        String plain = "lpk_AB12CD34EF56GH";
        req.addHeader("Authorization", "Bearer " + plain);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<RequestContext> captured = new AtomicReference<>();
        FilterChain chain = (r, s) -> captured.set(RequestContext.current());

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(captured.get().getAppId()).isEqualTo(99L);
        assertThat(captured.get().getScope()).isEqualTo(Scope.WRITE);
        verify(lastUsedTracker, times(1))
                .touch(eq(HashUtil.sha256Hex(plain)), eq(7L));
        // 退出后清理
        assertThat(RequestContext.current()).isNull();
    }

    @Test
    void should_log_only_key_prefix_no_hash_or_plaintext() throws Exception {
        ApiKey readKey = sampleKey(Scope.READ, KeyStatus.active, null);
        readKey.setKeyPrefix("lpk_AB12");
        when(apiKeyMapper.findByHash(anyString())).thenReturn(readKey);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs");
        String plain = "lpk_AB12SECRETPLAINTEXT";
        req.addHeader("Authorization", "Bearer " + plain);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        // 响应体不应包含明文或 hash
        String body = resp.getContentAsString();
        assertThat(body).doesNotContain("SECRETPLAINTEXT");
        assertThat(body).doesNotContain(HashUtil.sha256Hex(plain));
    }

    @Test
    void admin_token_check_should_be_constant_time_safe() throws Exception {
        // 验证：长度不同 / 仅最后一位不同的 token 都返回相同的 401，路径行为一致。
        // 严格的 constant-time 用 timing 验证不稳定，这里只做行为对称性断言。
        for (String wrong : new String[] {
                "x",
                ADMIN_TOKEN.substring(0, ADMIN_TOKEN.length() - 1) + "X",
                ADMIN_TOKEN + "X"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/apps");
            req.addHeader("Authorization", "Bearer " + wrong);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, (r, s) -> {});
            assertThat(resp.getStatus()).isEqualTo(401);
            assertThat(resp.getContentAsString()).contains(ErrorCode.AUTH_INVALID_ADMIN_TOKEN);
        }
    }

    private ApiKey sampleKey(Scope scope, KeyStatus status, Instant expiresAt) {
        ApiKey k = new ApiKey();
        k.setId(7L);
        k.setAppId(99L);
        k.setKeyPrefix("lpk_AB12");
        k.setKeyHash("hash-stub");
        k.setScope(scope);
        k.setStatus(status);
        k.setExpiresAt(expiresAt);
        k.setLabel("test-key");
        return k;
    }
}
