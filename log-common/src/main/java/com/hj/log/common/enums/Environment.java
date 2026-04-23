package com.hj.log.common.enums;

/** 应用部署环境。 */
public enum Environment {
    dev,
    staging,
    prod;

    public static Environment fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (Environment e : values()) {
            if (e.name().equalsIgnoreCase(code)) {
                return e;
            }
        }
        return null;
    }
}
