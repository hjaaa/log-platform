package com.hj.log.web.config;

import com.hj.log.source.config.AdminTokenProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 启动期校验 {@code log.platform.admin.token}，对应 detailed-design §8.1 fail-fast。
 *
 * <p>任一校验失败抛 {@link IllegalStateException}，使 Spring 容器启动失败，
 * 避免空或占位 token 进入生产。校验日志只输出长度与是否占位，不打印 token 值。
 */
@Component
public class AdminTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenValidator.class);

    /** 与 {@code .env.example} / detailed-design §8.1 保持一致的默认占位值。 */
    static final String DEFAULT_PLACEHOLDER = "replace-me-with-openssl-rand-hex-32";

    /** detailed-design §8.1：openssl rand -hex 32 输出 64 字符，最低 32 字符门槛作为冗余。 */
    static final int MIN_LENGTH = 32;

    private final AdminTokenProperties props;

    public AdminTokenValidator(AdminTokenProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void validate() {
        String token = props.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "LOG_PLATFORM_ADMIN_TOKEN 未配置；生成命令：openssl rand -hex 32");
        }
        if (DEFAULT_PLACEHOLDER.equals(token)) {
            throw new IllegalStateException(
                    "LOG_PLATFORM_ADMIN_TOKEN 仍为 .env.example 占位值，必须替换为真实值");
        }
        if (token.length() < MIN_LENGTH) {
            throw new IllegalStateException(
                    "LOG_PLATFORM_ADMIN_TOKEN 长度 " + token.length() + " 不足 " + MIN_LENGTH);
        }
        log.info("[startup] admin token validated, length={}", token.length());
    }
}
