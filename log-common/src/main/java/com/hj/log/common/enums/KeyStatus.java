package com.hj.log.common.enums;

/** API Key 生命周期状态。 */
public enum KeyStatus {
    active,
    revoked;

    public static KeyStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (KeyStatus s : values()) {
            if (s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        return null;
    }
}
