package com.hj.log.source.filter;

import com.hj.log.common.enums.Scope;

/**
 * 把 (HTTP method, request URI) 映射到所需 {@link Scope}。
 *
 * <p>规则（detailed-design §4.1）：
 * <ul>
 *   <li>{@code /api/v1/apps...} → {@link Scope#ADMIN}</li>
 *   <li>{@code POST /api/v1/logs} → {@link Scope#WRITE}</li>
 *   <li>{@code GET  /api/v1/logs/...} → {@link Scope#READ}</li>
 *   <li>其余 → {@code null}（豁免，调用方应已在更早阶段过滤）</li>
 * </ul>
 *
 * <p>路径前缀匹配只覆盖 v1；新版本应起独立 resolver。
 */
public final class EndpointResolver {

    private static final String API_V1 = "/api/v1";
    private static final String APPS_PREFIX = API_V1 + "/apps";
    private static final String LOGS_EXACT = API_V1 + "/logs";
    private static final String LOGS_PREFIX = API_V1 + "/logs/";

    private EndpointResolver() {
    }

    /**
     * @return 匹配到的 scope，或 {@code null} 表示路径不在管控范围内
     */
    public static Scope resolve(String method, String uri) {
        if (method == null || uri == null) {
            return null;
        }
        if (uri.startsWith(APPS_PREFIX)) {
            return Scope.ADMIN;
        }
        if ("POST".equalsIgnoreCase(method) && LOGS_EXACT.equals(uri)) {
            return Scope.WRITE;
        }
        if ("GET".equalsIgnoreCase(method) && (LOGS_EXACT.equals(uri) || uri.startsWith(LOGS_PREFIX))) {
            return Scope.READ;
        }
        return null;
    }
}
