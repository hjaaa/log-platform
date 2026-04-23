package com.hj.log.common.enums;

/**
 * API Key 与 admin token 的作用域。
 *
 * <ul>
 *   <li>{@link #ADMIN} —— 控制面 token；不入 api_keys 表，仅匹配 log.platform.admin.token</li>
 *   <li>{@link #WRITE} —— 业务应用写入 POST /api/v1/logs</li>
 *   <li>{@link #READ}  —— 查询面 GET /api/v1/logs/*</li>
 * </ul>
 */
public enum Scope {
    ADMIN,
    WRITE,
    READ;

    public static Scope fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (Scope s : values()) {
            if (s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        return null;
    }
}
