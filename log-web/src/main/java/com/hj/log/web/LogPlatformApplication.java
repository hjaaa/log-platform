package com.hj.log.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * log-platform 服务端唯一启动入口。
 *
 * <p>装配职责：
 * <ul>
 *   <li>组件扫描覆盖 {@code com.hj.log.*} 全部 module（source / ingestion / query / common / web）</li>
 *   <li>MyBatis Mapper 扫描覆盖三个业务 module 的 {@code mapper} 子包</li>
 *   <li>启用 {@code @Scheduled}（保留期清理）与 {@code @Async}（last_used_at 异步更新）</li>
 * </ul>
 *
 * <p>其他配置见 detailed-design §4.4 / §8；启动前校验由
 * {@link com.hj.log.web.config.AdminTokenValidator} 执行。
 */
@SpringBootApplication(scanBasePackages = "com.hj.log")
@EnableScheduling
@EnableAsync
@MapperScan(
        basePackages = {
            "com.hj.log.source.mapper",
            "com.hj.log.ingestion.mapper",
            "com.hj.log.query.mapper"
        })
public class LogPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogPlatformApplication.class, args);
    }
}
