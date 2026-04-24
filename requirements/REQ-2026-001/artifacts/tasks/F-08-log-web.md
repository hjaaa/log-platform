---
feature_id: F-08-log-web
title: log-web · 启动入口 + 全局异常 + 配置装配
status: done
type: feature
module: log-web
effort_pd: 1
depends_on: [F-05-log-source-ctrl, F-06-log-ingestion, F-07-log-query]
updated_at: 2026-04-24T10:55:00Z
retroactive_note: |
  commit d61cd5c（9 tests green）状态漂移补齐。design-consistency-checker
  追溯审查通过（issues: []），覆盖 5 项检查段：§4.4 GlobalExceptionHandler 三路径
  + §5.3 lpk_*** 正则脱敏、§5.1 logback pattern、§7.1 13 个配置项、§8.1 admin
  token fail-fast 三类占位/空/短长都抛、LogPlatformApplication @MapperScan
  覆盖 source/ingestion/query。task 状态由 in-progress 补齐为 done。
refs:
  - artifacts/detailed-design.md#44-全局异常处理comhjlogwebhandlerglobalexceptionhandler
  - artifacts/detailed-design.md#8-部署与运行
decisions:
  - "只落 AdminTokenValidator；Caffeine/Async/MyBatis/DataSource 复用各 module 既有 AutoConfig + Spring Boot 默认"
  - "logback-spring.xml 随本 feature 一起落"
  - "OpenApiConfig 延后至 F-10"
---

## 背景

唯一 Spring Boot 启动入口；负责扫包、装配 DataSource / MyBatis / Caffeine、注册全局异常处理、admin token fail-fast 校验。

## 实施清单

### Application

- `com.hj.log.web.LogPlatformApplication`
- `@SpringBootApplication(scanBasePackages = "com.hj.log")`
- `@EnableScheduling`、`@EnableAsync`、`@EnableConfigurationProperties`
- Mapper 扫描：`@MapperScan(basePackages = {"com.hj.log.source.mapper", "com.hj.log.ingestion.mapper", "com.hj.log.query.mapper"})`

### GlobalExceptionHandler（detail-design §4.4）

- `com.hj.log.web.handler.GlobalExceptionHandler`
- 三个 @ExceptionHandler：`BusinessException` / `MethodArgumentNotValidException` / `Exception`
- 兜底 `Exception` 必须脱敏（用正则替换 `lpk_***`）

### 配置类

- `DataSourceConfig`（HikariCP 默认）
- `MyBatisConfig`（mapper 路径 `classpath*:mapper/**/*.xml`）
- `CaffeineConfig`（auth-last-used cache bean）
- `AsyncConfig`（authExecutor 线程池）
- `OpenApiConfig`（暴露 `/openapi.yaml` 静态文件，二期切 springdoc）

### Fail-fast 校验

- `com.hj.log.web.config.AdminTokenValidator`：`@PostConstruct` 检查 `LOG_PLATFORM_ADMIN_TOKEN` 存在 + 长度 ≥ 32 + 非 `replace-me-with-openssl-rand-hex-32`；缺失 / 默认值 → 抛异常使容器启动失败

### Actuator

- `management.endpoints.web.exposure.include=health,info`
- `/actuator/health` 不需 Bearer（在 AuthFilter 豁免段已配）

### application.yml 关键段

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml

log:
  platform:
    admin:
      token: ${LOG_PLATFORM_ADMIN_TOKEN}
    retention:
      days: 7
    # ... 其余按 §7.1
```

## 验收标准

- [ ] 应用可启动：`source .env && mvn -pl log-web spring-boot:run`
- [ ] `GlobalExceptionHandler` 测试覆盖三种异常路径，响应不含堆栈
- [ ] 缺 `LOG_PLATFORM_ADMIN_TOKEN` 或为默认值时启动 fail-fast（容器抛异常退出）
- [ ] `/actuator/health` 暴露且不需 Bearer
- [ ] `/api/v1/apps` 用错误 token 返回 401 `AUTH_INVALID_ADMIN_TOKEN`
- [ ] Mapper 扫描覆盖 source/ingestion/query 三个 module 的 mapper 包
- [ ] `mvn -pl log-web test` 全绿
