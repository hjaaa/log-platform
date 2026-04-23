package com.hj.log.source.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * log-source 模块的 Spring 装配入口。被 log-web 通过组件扫描自动发现。
 *
 * <p>{@code @EnableAsync} 由 log-web 顶层启用；这里只暴露 properties 与 cache / executor
 * Bean 定义。
 */
@Configuration
@EnableConfigurationProperties({AdminTokenProperties.class, AuthProperties.class})
public class LogSourceAutoConfiguration {
}
