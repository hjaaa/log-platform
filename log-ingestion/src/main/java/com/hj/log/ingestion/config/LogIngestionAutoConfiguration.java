package com.hj.log.ingestion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** log-ingestion 模块装配入口。 */
@Configuration
@EnableConfigurationProperties({IngestionProperties.class, RetentionProperties.class})
public class LogIngestionAutoConfiguration {
}
