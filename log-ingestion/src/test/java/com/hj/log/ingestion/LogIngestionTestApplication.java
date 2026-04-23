package com.hj.log.ingestion;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** log-ingestion 模块自身集成测试启动类。生产部署不引用。 */
@SpringBootApplication(scanBasePackages = "com.hj.log.ingestion")
public class LogIngestionTestApplication {
}
