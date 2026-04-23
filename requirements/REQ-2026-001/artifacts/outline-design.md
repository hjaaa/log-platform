---
id: REQ-2026-001
title: 日志平台骨架模块 · 概要设计
created_at: 2026-04-23T11:10:00Z
phase: outline-design
refs-requirement: true
refs-tech-feasibility: true
---

# REQ-2026-001 · 概要设计（outline-design）

> 本文聚焦"模块划分 + 边界 + 数据流 + 骨架退役方案"四件事；表结构 DDL、接口签名、时序图等留到 detail-design。所有未带"待 detail-design 锁定"标注的决策视为本阶段定稿，可直接用于 detail-design 推进。

## 1. 设计目标

- 把 `requirement.md` + `tech-feasibility.md` 的结论收敛为一份"可被 detail-design 直接消费"的工程方案。
- 显式拆分本阶段必须落地的两件事：① 6 module 父子 pom 骨架；② 旧 `log-core` / `log-web` 骨架退役 commit 清单。
- 给后续阶段（detail-design / development）建立公共词汇：包命名、模块边界、数据流编号。

## 2. 整体架构

### 2.1 进程拓扑

- **单进程部署**：`log-web` 是唯一的 Spring Boot 启动入口，组装其余后端 5 个 module（`log-common` / `log-source` / `log-ingestion` / `log-query`，`log-sdk` 为客户端 jar，**不打包进 log-web**）。
- **独立客户端**：`log-sdk` 单独发布到内部 Maven 仓库，业务应用通过 `pom.xml` 依赖 + 配置项接入。
- **跨仓边界**：`log-platform` 只暴露 REST API；`mcp-platform` 仓的 `mcp-log-viewer` 持 read API Key，作为 Claude Code → log-platform 的中介（来源：../notes.md:90）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  业务应用 JVM                                                          │
│  ┌────────────────┐    write API Key    ┌─────────────────────────┐  │
│  │ 业务代码 logger │─────► log-sdk ─────►│ POST /api/v1/logs       │  │
│  └────────────────┘   非阻塞/不抛异常    └────────┬────────────────┘  │
└──────────────────────────────────────────────────┼────────────────────┘
                                                   │
                                                   ▼
                       ┌──────────────────────────────────────────────┐
                       │  log-platform (单 Spring Boot 进程)           │
                       │  ┌───────────────────────────────────────┐   │
                       │  │ log-web (启动/装配/全局异常)          │   │
                       │  └──┬──────────┬──────────┬──────────────┘   │
                       │     │          │          │                  │
                       │  ┌──▼──┐  ┌────▼─────┐  ┌─▼──────────┐      │
                       │  │log- │  │log-      │  │log-query   │      │
                       │  │source│ │ingestion │  │            │      │
                       │  └──┬──┘  └────┬─────┘  └─┬──────────┘      │
                       │     └──────────┴──────────┘                  │
                       │                │                             │
                       │            ┌───▼────┐                        │
                       │            │log-    │   公共 domain/exception│
                       │            │common  │                        │
                       │            └───┬────┘                        │
                       └────────────────┼────────────────────────────┘
                                        │
                                  ┌─────▼─────┐
                                  │  MySQL 8  │
                                  └───────────┘

                                        ▲
                                        │  read API Key
                       ┌────────────────┴──────────────────┐
                       │  mcp-platform (另一仓)             │
                       │  packages/mcp-log-viewer (MCP svr)│
                       └────────────────▲──────────────────┘
                                        │ MCP stdio
                                        │
                                ┌───────┴────────┐
                                │ Claude Code IDE│
                                └────────────────┘
```

### 2.2 6 module 划分

依赖方向：单向、无环；箭头表示"依赖于"。`log-sdk` 与后端解耦，仅作客户端 jar。

```
                           ┌──► log-ingestion ──┐
log-web (启动/组装) ───────┼──► log-query     ──┼──► log-common ──► (无)
                           └──► log-source    ──┘
log-sdk (独立客户端) ────────────────────────────► (无)
```

| module | 职责（单一） | 对外暴露 | 依赖 | 估时（人日） |
|---|---|---|---|---:|
| `log-common` | 共享 domain（`AppRegistration` / `ApiKey` / `LogEntry` 三个 DO 雏形）、enum（`Scope` / `LogLevel` / `LogKind` / `KeyStatus`）、`BusinessException`、`ResponseResult<T>`、`BaseEntity`、`db/schema.sql` 三表 DDL | Java 类（仓库内 module 引用） | 无 | 1 |
| `log-source` | 应用注册 / API Key 签发与撤销 / 鉴权 Filter（解析 `Authorization: Bearer`）/ 把 `appId+scope` 注入 `RequestContext` | REST `/api/v1/apps*` 控制面、Spring `Filter` Bean | `log-common` | 3 |
| `log-ingestion` | 接收批量日志、参数校验、转 DO、批量 INSERT、分批保留期清理（`LogRetentionTask`） | REST `POST /api/v1/logs`、`@Scheduled` Bean | `log-common`、`log-source` | 2 |
| `log-query` | 查询过滤、游标分页、按 traceId 关联、字段裁剪 | REST `GET /api/v1/logs/*` 读接口 | `log-common`、`log-source` | 3 |
| `log-web` | Spring Boot 启动入口、`GlobalExceptionHandler`、Filter 注册顺序、配置装配（DataSource / MyBatis / Caffeine / OpenAPI） | `main()`、Actuator | `log-source`、`log-ingestion`、`log-query` 3 个业务 module（`log-common` 通过传递依赖到达） | 1 |
| `log-sdk` | 客户端 jar：环形缓冲 + 批量 HTTP POST + 重试 + 失败降级本地文件 + replay；非阻塞、不抛异常 | Java API：`LogClient.write(LogEvent)`；SPI: `Logback`/`Slf4j` Appender 适配（适配器二期） | 无 | 4 |

**为什么 ingestion / query 不在 pom 上依赖 `log-source`**：

- 编译期：`log-ingestion`、`log-query` 不 import `log-source` 的任何类——它们读上下文走 `com.hj.log.common.context.RequestContext`（位于 `log-common`），而 `log-source` 的 `AuthFilter` 负责写入这个 ThreadLocal。三方共享的"接口"即 `RequestContext` 字段集，下沉在 `log-common`。
- 运行期：`log-source` 的 Controller / Service / Mapper 与 `AuthFilter` 由 `log-web` 的组件扫描挂载（`@SpringBootApplication(scanBasePackages = "com.hj.log")`），不需要被 ingestion/query 反向调用。
- 这是 module 层面的"接口隔离 + 共享数据契约下沉"，规避循环依赖。

### 2.3 包命名规范

根包统一 `com.hj.log`（沿用项目既有约定，来源：context/project/log-platform/CLAUDE.md:18）。各 module 在根包下用 module 短名做二级前缀：

| module | 包前缀 | 子包（默认） |
|---|---|---|
| `log-common` | `com.hj.log.common` | `.domain` · `.enums` · `.exception` · `.base` · `.context` · `.util` |
| `log-source` | `com.hj.log.source` | `.controller` · `.service` · `.mapper` · `.filter` · `.dto` |
| `log-ingestion` | `com.hj.log.ingestion` | `.controller` · `.service` · `.mapper` · `.dto` · `.task` |
| `log-query` | `com.hj.log.query` | `.controller` · `.service` · `.mapper` · `.dto` · `.cursor` |
| `log-web` | `com.hj.log.web` | `.config` · `.handler`（全局异常）· `LogPlatformApplication.java` |
| `log-sdk` | `com.hj.log.sdk` | `.client` · `.buffer` · `.transport` · `.fallback` · `.config` · `.model` |

**约束**：

- 跨 module 的 import 只允许指向 `com.hj.log.common.*`；其他 module 间禁止互相 import（`log-web` 通过 Spring Bean 装配，不直接 import）。
- MyBatis Mapper XML 路径统一 `<module>/src/main/resources/mapper/<Module>/*.xml`，子目录区分归属（避免日后 mapper 重名）。
- DTO（入参 `*Request` / 出参 `*Response` / 内部传输 `*Command`）只放在所属 module 的 `.dto` 子包；不进 `log-common`。

### 2.4 配置项命名规范（detail-design 落地，本阶段定 prefix）

两个独立命名空间，互不混用：

- **服务端**（log-platform 进程）一律 `log.platform.*`
- **SDK**（log-sdk，被业务应用宿主进程加载）一律 `log.sdk.*`——SDK 与 log-platform 不在同一 JVM，分两个 prefix 避免与宿主应用其他 `log.platform.*` 配置冲突。

| 命名空间 | 用途 | 示例 |
|---|---|---|
| `log.platform.admin.token` | 管理面 token（不入 api_keys 表） | 注入 `LOG_PLATFORM_ADMIN_TOKEN` 环境变量 |
| `log.platform.retention.days` | 日志保留天数（MVP 默认 7） | `7` |
| `log.platform.retention.batch-size` | 保留期 DELETE 单批行数 | `500` |
| `log.platform.auth.last-used.cache-ttl-seconds` | `last_used_at` 节流 TTL | `60` |
| `log.platform.ingestion.max-batch-size` | 单批日志上限 | `200` |
| `log.platform.query.default-page-size` | 查询默认 pageSize | `100` |
| `log.platform.query.max-page-size` | 查询 pageSize 上限 | `500` |
| `log.sdk.fallback-dir` | SDK 失败降级目录 | `${user.home}/.log-sdk/fallback/` |
| `log.sdk.flush.batch-size` | SDK flush 触发条数 | `100` |
| `log.sdk.flush.interval-ms` | SDK flush 触发周期 | `2000` |

## 3. 关键数据流

### 3.1 写入数据流（编号 W1–W7，detail-design 据此画时序图）

| 步骤 | 责任方 | 动作 |
|---|---|---|
| W1 | 业务代码 | `logger.error(...)` 触发 → SDK 转换为 `LogEvent` |
| W2 | log-sdk | 入 `LinkedBlockingDeque(1024)`；满则丢最旧并 WARN（不阻塞业务线程） |
| W3 | log-sdk | 后台线程满 100 条 OR 每 2s flush → `POST /api/v1/logs`（携带 write key） |
| W4 | log-source AuthFilter | 校验 `key_hash` + `scope=write`；命中 Caffeine（TTL 60s）跳过 `last_used_at` UPDATE；写 `RequestContext` |
| W5 | log-ingestion Controller | `@Valid` 校验单批 ≤ 200、`message` 非空、`level` 合法 |
| W6 | log-ingestion Service | DTO → `LogEntry`；补 `app_id`（取 RequestContext）+ `server_ts`；`context_data` 序列化为 JSON 字符串 |
| W7 | log-ingestion Mapper | 单 SQL 多值 `INSERT`；返回 `200` + `ResponseResult<{accepted:N, rejected:[…]}>`（统一走仓库既有响应包装，**不**用 `207 Multi-Status`，避免 mcp-log-viewer 端额外解析 multi-status XML）；SDK 部分失败指数退避重试，重试耗尽降级 `log.sdk.fallback-dir` |

### 3.2 查询数据流（编号 Q1–Q6）

| 步骤 | 责任方 | 动作 |
|---|---|---|
| Q1 | mcp-log-viewer / curl | `GET /api/v1/logs/search?…`（携带 read key） |
| Q2 | log-source AuthFilter | 校验 `key_hash` + `scope=read`；写 `RequestContext` |
| Q3 | log-query Controller | 校验入参；未传 `appCode` 时强制要求 `traceId`，避免盲扫 |
| Q4 | log-query Service | 构建 `LogSearchCriteria`；游标解码：`base64 → server_ts + id` |
| Q5 | log-query Mapper | 走索引：`WHERE app_id=? AND level=? AND (server_ts,id)<(?,?) ORDER BY server_ts DESC, id DESC LIMIT ?` |
| Q6 | log-query Controller | 包 `ResponseResult<Page<LogView>>` + `nextCursor`，按 `fields=` 裁剪后返回 |

### 3.3 后台任务

| 任务 | 触发 | 实现 module | 关键约束 |
|---|---|---|---|
| 保留期清理 | `@Scheduled` 每小时 | log-ingestion (`LogRetentionTask`) | 分批 500 行 + `SLEEP(10ms)`；避免大事务 |
| `last_used_at` 异步合并（二期入口） | — | log-source | MVP 走同步 + Caffeine 节流；`detail-design` 留扩展点 |

## 4. 跨切面设计

### 4.1 鉴权与 RequestContext

- 所有 `/api/v1/**` 入口（除 `/api/v1/health`）必须经 `AuthFilter`。
- `AuthFilter` 流程：解析 Authorization header → 区分 `admin` / `apiKey`；前者比对 `log.platform.admin.token`，后者查 `api_keys` + `scope` 校验 → 写入 `RequestContext`。
- `RequestContext` 字段：`appId` (Long)、`scope` (`Scope` enum)、`keyId` (Long)、`requestId` (String, MDC 同步)；存放于 `ThreadLocal`，请求结束清理。
- admin scope 不入 `api_keys` 表，仅控制面 4 个 endpoint 接受；任何用 admin token 访问 write/read 端点的请求 → 403。

### 4.2 异常与响应

- `BusinessException`（`com.hj.log.common.exception`）承载所有可预期失败；`GlobalExceptionHandler`（`com.hj.log.web.handler`）统一捕获 → `ResponseResult.fail(code, msg)`；不暴露堆栈。
- 未知异常 → `500 ResponseResult.fail("INTERNAL_ERROR", "服务暂时不可用")`，详细 stacktrace 落 ERROR 日志含 requestId。
- 错误码命名：`<MODULE>_<KIND>`，例：`AUTH_INVALID_KEY` / `INGEST_BATCH_TOO_LARGE` / `QUERY_MISSING_APP_FILTER`。完整字典 detail-design 落地。

### 4.3 日志规范（自身服务内部日志）

- 所有 INFO/WARN/ERROR 必须带 `requestId`；通过 MDC + Logback `%X{requestId}` 模板。
- 禁止打印完整 `Authorization` header；`AuthFilter` 验证失败日志只输出 `key_prefix`（即 api_keys 表中存的密钥前 8 位字段，本身已脱敏）+ `scope`。
- 业务节点（应用注册、key 签发/撤销、批量写入数量、查询命中数）走 INFO；可恢复异常（key 过期、参数非法）走 WARN；DB / 第三方失败走 ERROR。

## 5. 骨架退役方案（R1 缓解动作）

> 本节是 **outline-design 阶段后续要执行的独立 commit 计划**，本文档定稿即视为方案锁定，commit 由人确认后执行。

### 5.1 现状清单（基于 `find log-core log-web -type f`）

旧 `log-core`：

```
log-core/pom.xml
log-core/src/main/java/com/hj/log/common/base/{BaseController.java, BaseEntity.java, ResponseResult.java}
log-core/src/main/java/com/hj/log/common/exception/BusinessException.java
log-core/src/main/java/com/hj/log/domain/{AlertRule.java, LogEntry.java, LogSource.java, NotificationConfig.java}
log-core/src/main/java/com/hj/log/mapper/{AlertRuleMapper.java, LogEntryMapper.java, LogSourceMapper.java, NotificationConfigMapper.java}
log-core/src/main/resources/db/schema.sql
log-core/src/main/resources/mapper/{AlertRuleMapper.xml, LogEntryMapper.xml, LogSourceMapper.xml, NotificationConfigMapper.xml}
log-core/src/test/java/com/hj/log/{CoreTestApplication.java, LogCoreSmokeTest.java, mapper/MapperIntegrationTest.java}
```

旧 `log-web`：

```
log-web/pom.xml
log-web/src/main/java/com/hj/log/{LogApplication.java, HealthController.java, common/handler/GlobalExceptionHandler.java}
log-web/src/test/java/com/hj/log/{LogApplicationTests.java, TestExceptionController.java, GlobalResponseAndExceptionHandlerTest.java}
```

### 5.2 退役步骤（拆为 2 个独立 commit）

**Commit A · `chore(skeleton): archive 旧 log-core / log-web 骨架`**

1. 创建 `_archive/` 目录（与 `requirements/` 同级）。
2. `git mv log-core _archive/log-core-skeleton`
3. `git mv log-web _archive/log-web-skeleton`
4. 编辑根 `pom.xml`：从 `<modules>` 删除 `log-core` 和 `log-web` 两行；不动 `dependencyManagement` 与 `properties`（将被新 module 复用）。
5. 在 `_archive/README.md` 写明：归档原因（REQ-2026-001 重新设计）、`git log --follow _archive/log-core-skeleton/<file>` 可追原历史。
6. **校验**：`mvn -q -N validate`（`-N` non-recursive，仅校验父 pom 自身；空 `<modules>` 段落是合法状态）。

**Commit A 与 Commit B 之间的窗口期**：根 `pom.xml` 的 `<modules>` 段允许为空，CI 仅跑 `mvn -q -N validate`；不要在窗口期触发 `mvn package`，以免误以为构建失败。建议两个 commit 在同一 PR 里相邻提交以缩短窗口。

**Commit B · `feat(skeleton): 建立 6 module 父子 pom 骨架`**（在 detail-design 落定 schema 后再做，本阶段不执行）

1. 在根目录新建 6 个空 module 目录与最小 `pom.xml`（仅 `<artifactId>` + `<parent>`）。
2. 根 `pom.xml` 的 `<modules>` 注册 6 个新 module（按依赖顺序：`log-common` 在前，`log-web` 在后）。
3. 每个 module 创建标准 `src/main/java/com/hj/log/<module-prefix>/` 与 `src/test/java` 空目录（含 `.gitkeep`）。
4. **校验**：`mvn -q -DskipTests clean compile` 在 6 module 结构下通过。

### 5.3 PR 描述模板（要点）

- 标题：`chore(skeleton): 退役旧 log-core/log-web、建立 6 module 父子骨架`
- Body 必含：
  - 退役原因：REQ-2026-001 重新设计（链 `requirements/REQ-2026-001/artifacts/outline-design.md`）
  - 与历史的关系：`_archive/` 目录 `git log --follow` 可继续追溯
  - 新 `log-web` 与旧 `log-web` 同名但不同语义的明确说明
  - revert 路径：`git revert <Commit A> <Commit B>` 即回到旧骨架

## 6. 与既有规范/文档的同步项（R7 缓解）

outline-design 定稿后，`context/project/log-platform/CLAUDE.md` 必须独立 `docs:` commit 同步以下内容（具体 patch 在 detail-design 阶段输出）：

| 章节 | 操作 |
|---|---|
| 构建与运行命令 | `mvn -pl log-web spring-boot:run` 改为 `mvn -pl log-web spring-boot:run`（module 名称未变但需校验示例命令在 6 module 结构下仍有效）；`mvn -pl log-core test` 改为 `mvn -pl log-common test` 或按 module 列出 |
| 模块结构 | 替换为本文 §2.2 的 6 module + 依赖图 |
| 分层约定（包路径） | 替换为本文 §2.3 的包前缀表 |
| 测试约定 | 增加 `log-sdk` 用 MockWebServer 的约定 |
| schema 路径 | 改为 `log-common/src/main/resources/db/schema.sql` |
| 「告警规则核心字段」整段 | 删除（MVP 不做） |
| 配置项命名空间 | 新增小节（参照本文 §2.4） |

## 7. 本阶段未决事项（移交 detail-design）

| 项 | 说明 | 接收阶段 |
|---|---|---|
| 三表完整 DDL（含字段类型、索引、注释） | notes.md §表结构 是初稿，detail-design 出最终 SQL | detail-design |
| 8 个 endpoint 完整签名（请求/响应/错误码） | OpenAPI YAML 落 `docs/api/v1/openapi.yaml` | detail-design |
| `log-sdk` 内部线程模型与 shutdown hook | flush 线程 / replay 线程分工、关闭顺序 | detail-design |
| 错误码完整字典 | 配合 GlobalExceptionHandler 输出 | detail-design |
| `LOG_PLATFORM_ADMIN_TOKEN` 注入与 `.env.example` | 含生成命令、Docker secret 示例 | detail-design |
| `features.json` 拆分 | 按 6 module + SDK + 退役 commit 拆 feature | detail-design |
| 跨仓 PR 模板 checkbox 落地（R6） | `.github/pull_request_template.md` 内容 | detail-design |
| `_archive/` 是否需要纳入 `mvn` 黑名单 | 评估是否会被某些插件误扫 | detail-design |

## 8. 评审要点（供 outline-design-quality-reviewer 检查）

- [ ] 模块边界单一，依赖无环；本文 §2.2 已给出依赖图与"为什么不依赖 log-source"说明。
- [ ] 包命名与项目既有 `com.hj.log` 根包对齐；本文 §2.3 表内已映射。
- [ ] 跨模块 import 约束显式（只允许 `log-common`）；本文 §2.3 约束段。
- [ ] 数据流可被 detail-design 直接画时序图；本文 §3 已编号 W1–W7 / Q1–Q6。
- [ ] 鉴权、异常、日志三个跨切面都有归属 module；本文 §4。
- [ ] R1（骨架退役）有可执行 commit 清单；本文 §5。
- [ ] R7（CLAUDE.md 同步）显式列出 patch 范围；本文 §6。
- [ ] 未决项明确移交 detail-design，未掩盖或漏项；本文 §7。
