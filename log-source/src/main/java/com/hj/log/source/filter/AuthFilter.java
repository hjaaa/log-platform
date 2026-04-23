package com.hj.log.source.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.common.base.ResponseResult;
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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 鉴权与 RequestContext 注入。流程见 detailed-design §4.1。
 *
 * <p>核心约束：
 * <ul>
 *   <li>admin token 比较走常量时间（{@link MessageDigest#isEqual}），防时序攻击</li>
 *   <li>失败日志只输出 {@code key_prefix} + {@code scope}，不出现 hash / 明文</li>
 *   <li>{@code last_used_at} 走 {@link LastUsedTracker} 异步更新 + Caffeine 节流，不阻塞业务线程</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION = "Authorization";
    private static final String EXEMPT_HEALTH = "/api/v1/health";
    private static final String EXEMPT_ACTUATOR_PREFIX = "/actuator/";

    private final AdminTokenProperties adminProps;
    private final ApiKeyMapper apiKeyMapper;
    private final LastUsedTracker lastUsedTracker;
    private final ObjectMapper objectMapper;

    public AuthFilter(
            AdminTokenProperties adminProps,
            ApiKeyMapper apiKeyMapper,
            LastUsedTracker lastUsedTracker,
            ObjectMapper objectMapper) {
        this.adminProps = adminProps;
        this.apiKeyMapper = apiKeyMapper;
        this.lastUsedTracker = lastUsedTracker;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith(EXEMPT_ACTUATOR_PREFIX) || EXEMPT_HEALTH.equals(uri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Scope endpointScope = EndpointResolver.resolve(request.getMethod(), request.getRequestURI());
        if (endpointScope == null) {
            // 不在管控范围（例如未来新增的非 v1 路径）—— 直接通过，由其他安全层兜底
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeError(response, 401, ErrorCode.AUTH_MISSING_TOKEN, "缺少 Authorization");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeError(response, 401, ErrorCode.AUTH_MISSING_TOKEN, "缺少 Authorization");
            return;
        }

        try {
            if (endpointScope == Scope.ADMIN) {
                handleAdmin(token, request, response, chain);
            } else {
                handleApiKey(token, endpointScope, request, response, chain);
            }
        } finally {
            RequestContextWriter.clear();
        }
    }

    private void handleAdmin(
            String token, HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String configured = adminProps.getToken();
        if (configured == null || !constantTimeEquals(token, configured)) {
            log.warn("[auth] admin token 不匹配 uri={}", request.getRequestURI());
            writeError(response, 401, ErrorCode.AUTH_INVALID_ADMIN_TOKEN, "管理 token 无效");
            return;
        }
        RequestContextWriter.set(null, Scope.ADMIN, null, MDC.get("requestId"));
        chain.doFilter(request, response);
    }

    private void handleApiKey(
            String token,
            Scope endpointScope,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        String hash = HashUtil.sha256Hex(token);
        ApiKey key = apiKeyMapper.findByHash(hash);
        if (key == null) {
            log.warn("[auth] key 不存在 uri={} prefixUnknown=true", request.getRequestURI());
            writeError(response, 401, ErrorCode.AUTH_INVALID_KEY, "API Key 无效或已撤销");
            return;
        }
        String prefix = key.getKeyPrefix();
        if (key.getStatus() == KeyStatus.revoked) {
            log.warn("[auth] key 已撤销 prefix={} scope={}", prefix, key.getScope());
            writeError(response, 401, ErrorCode.AUTH_INVALID_KEY, "API Key 无效或已撤销");
            return;
        }
        Instant expiresAt = key.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            log.warn("[auth] key 已过期 prefix={} scope={}", prefix, key.getScope());
            writeError(response, 401, ErrorCode.AUTH_INVALID_KEY, "API Key 无效或已撤销");
            return;
        }
        if (key.getScope() != endpointScope) {
            log.warn(
                    "[auth] scope 不匹配 prefix={} keyScope={} required={}",
                    prefix,
                    key.getScope(),
                    endpointScope);
            writeError(response, 403, ErrorCode.AUTH_SCOPE_MISMATCH, "API Key scope 不允许此操作");
            return;
        }

        RequestContextWriter.set(key.getAppId(), key.getScope(), key.getId(), MDC.get("requestId"));
        lastUsedTracker.touch(hash, key.getId());
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ResponseResult.fail(code, message));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }
}
