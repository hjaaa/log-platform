package com.hj.log.common.enums;

/**
 * 日志类型枚举。
 *
 * <ul>
 *   <li>{@link #application} —— 业务应用日志（默认）</li>
 *   <li>{@link #access}      —— 访问日志（HTTP 请求轨迹）</li>
 *   <li>{@link #test}        —— 测试输出（mvn test / CI）</li>
 * </ul>
 *
 * <p>枚举值小写以保持与 DB 存储字段一致（VARCHAR 直接序列化），简化 Mapper 转换。
 */
public enum LogKind {
    application,
    access,
    test;

    public static LogKind fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (LogKind k : values()) {
            if (k.name().equalsIgnoreCase(code)) {
                return k;
            }
        }
        return null;
    }
}
