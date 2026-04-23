package com.hj.log.common.enums;

/** 应用注册启用状态。 */
public enum AppStatus {
    active,
    disabled;

    public static AppStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AppStatus s : values()) {
            if (s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        return null;
    }
}
