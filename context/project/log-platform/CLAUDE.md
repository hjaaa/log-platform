# log-platform 项目规范

> 架构基线：REQ-2026-001 锁定的 6 module + 1 SDK + 3 表方案。详见 `requirements/REQ-2026-001/artifacts/outline-design.md` 与 `detailed-design.md`。

## 构建与运行

```bash
# 编译所有模块
mvn clean compile

# 打包（跳过测试）
mvn clean package -DskipTests

# 启动应用（log-web 是唯一启动入口；log-sdk 是客户端 jar，不启动）
source .env && mvn -pl log-web spring-boot:run

# 运行所有测试
mvn test

# 运行指定模块测试
mvn -pl log-common test
mvn -pl log-source test
mvn -pl log-ingestion test
mvn -pl log-query test
mvn -pl log-web test
mvn -pl log-sdk test

# 运行单个测试类
mvn -pl log-source test -Dtest=AuthFilterTest
```

> 旧 `log-core` / `log-web` 已归档至 `_archive/`，详见 `_archive/README.md`。

## 模块结构

```
log-platform
├── log-common      — 共享 domain / enum / exception / RequestContext / DDL（无 Spring Web 依赖）
├── log-source      — 应用注册 + API Key + AuthFilter（控制面 + 鉴权）
├── log-ingestion   — 批量日志写入 + 保留期清理任务
├── log-query       — 日志查询 + trace 关联 + 游标分页
├── log-web         — Spring Boot 启动入口 + 全局异常 + 配置装配
└── log-sdk         — 独立客户端 jar：异步缓冲 + HTTP POST + 失败降级（不参与服务端进程）
```

依赖方向（单向无环）：

```
                           ┌──► log-ingestion ──┐
log-web (启动/组装) ───────┼──► log-query     ──┼──► log-common
                           └──► log-source    ──┘
log-sdk (独立客户端)  ────────────────────────────► (无)
```

**根包**：`com.hj.log`

## 分层约定

各 module 的包前缀严格对应模块短名；跨 module 的 import 只允许指向 `com.hj.log.common.*`。

| module | 包前缀 | 子包 |
|---|---|---|
| log-common | `com.hj.log.common` | `.domain` / `.enums` / `.exception` / `.base` / `.context` / `.util` |
| log-source | `com.hj.log.source` | `.controller` / `.service` / `.mapper` / `.filter` / `.dto` |
| log-ingestion | `com.hj.log.ingestion` | `.controller` / `.service` / `.mapper` / `.dto` / `.task` |
| log-query | `com.hj.log.query` | `.controller` / `.service` / `.mapper` / `.dto` / `.cursor` |
| log-web | `com.hj.log.web` | `.config` / `.handler`（全局异常）+ `LogPlatformApplication` |
| log-sdk | `com.hj.log.sdk` | `.client` / `.buffer` / `.transport` / `.fallback` / `.config` / `.model` |

通用职责约束：

| 层 | 职责 |
|---|---|
| Controller | 参数校验（`@Valid`）、调用 Service、返回 `ResponseResult<T>`；不直接读 RequestContext 之外的全局状态 |
| Service | 业务编排；不直接操作 Mapper XML 拼接 |
| Mapper | MyBatis 接口，XML 落 `<module>/src/main/resources/mapper/<module>/*.xml`，子目录隔离避免跨 module 重名 |
| Domain | DO 仅含字段 + getter/setter，不写业务方法 |

- **禁止**把业务逻辑写入 Mapper XML
- DTO（入参 `*Request` / 出参 `*Response`）与 DO 分离，**只放在所属 module 的 `.dto` 包**，不进 `log-common`

## 核心公共组件（落在 log-common）

- `ResponseResult<T>`（`com.hj.log.common.base`）：统一响应包装；Controller 全部走这个
- `BusinessException`（`com.hj.log.common.exception`）：业务异常，含 `code` / `message` / `httpStatus`
- `ErrorCode`（`com.hj.log.common.exception`）：错误码常量字典，命名规范 `<MODULE>_<KIND>`
- `BaseEntity`（`com.hj.log.common.base`）：含 `id`、`createdAt`、`updatedAt`；所有 DO 继承
- `RequestContext`（`com.hj.log.common.context`）：ThreadLocal，承载 `appId` / `scope` / `keyId` / `requestId`；**`set` 包私有**，仅 AuthFilter / RequestIdFilter 可写
- `GlobalExceptionHandler`（`com.hj.log.web.handler`）：捕获 `BusinessException` / `MethodArgumentNotValidException` / `Exception`，返回标准错误体并对 token 脱敏

## 配置项命名空间

两个独立 prefix，互不混用：

| 前缀 | 适用进程 | 示例 key |
|---|---|---|
| `log.platform.*` | log-platform 服务端 JVM | `log.platform.admin.token` / `log.platform.retention.days` / `log.platform.ingestion.max-batch-size` |
| `log.sdk.*` | log-sdk 宿主（业务应用）JVM | `log.sdk.endpoint` / `log.sdk.api-key` / `log.sdk.flush.batch-size` / `log.sdk.fallback-dir` |

完整清单见 `requirements/REQ-2026-001/artifacts/detailed-design.md` §7。

## 数据库

- 生产：MySQL 8+（schema 落 `log-common/src/main/resources/db/schema.sql`）
- 测试：H2 内存数据库（`@MybatisTest` + `@AutoConfigureTestDatabase`）
- 三张表：`app_registrations` / `api_keys` / `logs`
- JSON 字段（`context_data`）当前以 `TEXT` 存 JSON 字符串，业务层手动序列化；二期可升 `JSON` 类型支持 path 检索

## 测试约定

- **Mapper 层**：`@MybatisTest` + H2，测试类落在所属 module（如 `log-source`、`log-ingestion`、`log-query`）
- **Web 层**：`@SpringBootTest` + MockMvc，测试类落 `log-web`
- **SDK**：`MockWebServer`（OkHttp）+ JUnit 5；覆盖正常 flush / 5xx 重试耗尽 / 4xx 丢弃 / shutdown hook / fallback replay / 字段截断 / 队列满丢弃 7 个场景
- 命名：`should_xxx_when_yyy` 或 `given_yyy_when_xxx_then_zzz`

## 鉴权与 RequestContext

- 所有 `/api/v1/**`（除 `/actuator/**`）经 `AuthFilter`：admin token（控制面 4 endpoint）/ API Key（write/read scope）双路径
- `last_used_at` UPDATE 走 Caffeine 节流（`expireAfterWrite=60s`）+ `@Async` 异步线程池，业务请求不阻塞
- 业务层取 `appId` / `scope` 必须经 `RequestContext.current()`，禁止直接 import `log-source` 的 Filter
