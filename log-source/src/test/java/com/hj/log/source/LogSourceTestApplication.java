package com.hj.log.source;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 仅供 log-source 模块自身集成测试（{@code @MybatisTest} 等）使用的最小启动类。
 *
 * <p>不被打包到生产包；扫描根包以让 {@code @Mapper} 接口能被探测到。
 */
@SpringBootApplication(scanBasePackages = "com.hj.log.source")
public class LogSourceTestApplication {
}
