package com.hj.log.query.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** log-query 模块装配入口。 */
@Configuration
@EnableConfigurationProperties(QueryProperties.class)
public class LogQueryAutoConfiguration {
}
