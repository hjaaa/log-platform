package com.hj.log.common.enums;

/** 日志级别枚举（与 Logback 级别对齐）。 */
public enum LogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG;

    public static LogLevel fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (LogLevel l : values()) {
            if (l.name().equalsIgnoreCase(code)) {
                return l;
            }
        }
        return null;
    }
}
