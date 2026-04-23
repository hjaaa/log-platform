package com.hj.log.source.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin token 配置；前缀 {@code log.platform.admin}。
 *
 * <p>fail-fast 校验落 log-web 的启动钩子（见 detailed-design §8.1）；本类仅承载值。
 */
@ConfigurationProperties(prefix = "log.platform.admin")
public class AdminTokenProperties {

    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
