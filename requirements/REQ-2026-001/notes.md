# REQ-2026-001 · 设计归档 notes

本文件是 definition 阶段 brainstorming 的 HOW 层面归档，供后续 outline-design / detail-design 阶段消费。所有内容均为**初稿**——后续阶段可基于此细化或调整。

## Brainstorming 元信息

- 日期：2026-04-23
- 讨论主题：日志平台骨架模块的模块划分（"先忽略项目中已有的项目代码"）
- 决策人：huangjian
- 协作方式：Claude Code + superpowers:brainstorming skill + visual companion

## 模块划分（6 个 Maven module）

| module | 单一职责 | 对外暴露 | 依赖 |
|---|---|---|---|
| log-sdk | 客户端 jar：环形缓冲 + 批量 HTTP POST + 重试/降级；非阻塞；失败永不抛出 | Java API：`LogClient.write(LogEvent)` | 无（独立发布） |
| log-common | 共享 domain / enum / exception / 工具 | Java 类（供项目内其它 module 用） | 无 |
| log-source | 应用注册、API Key 管理、鉴权 Filter | REST `/api/v1/apps` 控制面 · Spring Filter 挂 RequestContext | log-common |
| log-ingestion | 接收日志、校验、批量落库 | REST `POST /api/v1/logs` | log-common, log-source |
| log-query | 查询、过滤、游标分页、traceId 关联 | REST `/api/v1/logs/*` 读接口 | log-common, log-source |
| log-web | Spring Boot 启动入口 · 全局异常处理 · 配置装配 | `main()` | 以上全部（除 log-sdk） |

依赖关系（ASCII）：

```
log-sdk    (独立客户端)
                            ┌──► log-ingestion ──┐
log-web  (启动/组装) ───────┼──► log-query     ──┼──► log-common
                            └──► log-source    ──┘
                                                  ↓
                                               MySQL
```

## 表结构初稿（3 张表）

### app_registrations（应用注册 · 元信息）
- `id` BIGINT PK · `code` VARCHAR UNIQUE（业务编码，如 order-service）· `name` · `owner` · `environment`（dev/staging/prod）· `status`（active/disabled）· `created_at` · `updated_at`
- UK：`uk_app_code(code)`

### api_keys（密钥 · 鉴权）
- `id` PK · `app_id` FK → apps.id · `key_prefix`（前 8 位展示用）· `key_hash`（SHA-256）· `scope`（write / read）· `status`（active / revoked）· `last_used_at` · `expires_at`（可空）· `created_at`
- UK：`uk_api_key_hash(key_hash)`

### logs（日志主表）
- `id` BIGINT PK · `app_id` BIGINT（**软引用**，无 FK，写入热路径不跨表约束）
- 控制：`log_kind`（application/access/test）· `level`（ERROR/WARN/INFO/DEBUG）
- 内容：`message` TEXT · `stack_trace` TEXT（可空）· `context_data` TEXT(JSON 字符串，可空)
- 追溯：`trace_id` VARCHAR(64) · `span_id` VARCHAR(32) · `request_id` VARCHAR(64)（均可空）
- 环境：`logger_name` · `thread_name` · `source_host`（均可空）
- 时间：`client_ts` DATETIME(3) · `server_ts` DATETIME(3)

### 核心索引（面向 agent 查询模式）

| 索引 | 覆盖 |
|---|---|
| `idx_logs_app_ts(app_id, server_ts DESC)` | "某应用最近 N 分钟日志" |
| `idx_logs_trace(trace_id)` | "按 traceId 关联调用链" |
| `idx_logs_app_level_ts(app_id, level, server_ts DESC)` | "某应用最近的 ERROR" |
| `idx_logs_kind_ts(log_kind, server_ts DESC)` | "最近的测试失败输出" |

### 表结构决策

- logs 单表不分区（10 万/日 MySQL 撑得住；二期按 server_ts 月分区）
- JSON 字段用 TEXT 存 JSON 字符串（延续 CLAUDE.md 约定）
- 枚举用 VARCHAR + 代码层校验（不用 MySQL ENUM，迁移友好）
- 主键 BIGINT 自增（MVP 单库；二期视规模切雪花 ID）
- 保留 7 天用 Spring Scheduled + DELETE（二期换 DROP PARTITION）
- api_keys.last_used_at 每次写入都 UPDATE（MVP 10 应用规模无压力；二期改异步合并更新）

## REST 契约初稿（8 个 endpoint）

| Endpoint | scope | 说明 |
|---|---|---|
| `POST /api/v1/apps` | admin | 注册应用，返回 appId（无 key） |
| `GET /api/v1/apps` | admin | 列表 / 按 code 查 |
| `POST /api/v1/apps/{id}/keys` | admin | 签发 key（body 含 scope=write\|read），响应仅一次返回明文 |
| `DELETE /api/v1/apps/{id}/keys/{keyId}` | admin | 撤销 key（status=revoked） |
| `POST /api/v1/logs` | write | 批量写入，单批 ≤ 200；响应列出 rejected 明细 |
| `GET /api/v1/logs/search` | read | 按 appCode/level/time window/traceId/keyword 过滤；游标分页 |
| `GET /api/v1/logs/{id}` | read | 单条详情 |
| `GET /api/v1/logs/trace/{traceId}` | read | 便捷端点：按 traceId 拉齐整条调用链，默认 ≤ 500 条 |

### 契约约定

- admin scope 用配置里的 `LOG_PLATFORM_ADMIN_TOKEN`，不走 api_keys 表
- 游标 = base64(server_ts + id)，避免 offset 深分页
- 字段裁剪 `fields=a,b,c`（节省 agent token）
- 响应统一 `ResponseResult<T>`（沿用仓库约定）

## MCP 工具清单（跨仓，留给 mcp-platform 的独立需求）

本需求**不交付** MCP 实现；仅定义 mcp-log-viewer 可消费的 REST 端点。mcp-log-viewer 计划暴露的工具：

| Tool | 底层 REST 端点 | 说明 |
|---|---|---|
| `searchLogs` | `GET /api/v1/logs/search` | 关键字/时间窗/级别过滤 |
| `getTrace` | `GET /api/v1/logs/trace/{traceId}` | 一次拉整条调用链 |
| `getLog` | `GET /api/v1/logs/{id}` | 单条详情 |

mcp-log-viewer 内部持 read API Key（通过环境变量注入），Claude Code 不接触 token。

## 技术栈约束

- JDK 21 · Spring Boot 3.x · Maven 多 module
- MyBatis 持久层
- MySQL 8+（生产）· H2（Mapper 单元测试）
- API Key hash = SHA-256
- SDK 内部 HTTP client 推荐 OkHttp 或 JDK 原生 HttpClient（detail-design 定）

## 写入/查询数据流

### 写入（7 步）
1. 业务代码 `logger.error(...)` → SDK 收到 LogEvent，放入内存环形缓冲区（非阻塞）
2. SDK 后台线程定时或满 100 条 flush → `POST /api/v1/logs` + `Authorization: Bearer <write-key>`
3. log-source AuthFilter 拦截 → 查 api_keys.key_hash → 校验 scope=write → 把 appId 挂 RequestContext
4. log-ingestion Controller `@Valid` 校验（批 ≤ 200、message 非空、level 合法）
5. Service：DTO → LogEntry（取 appId、补 server_ts、序列化 context_data）
6. LogMapper 单 SQL 多值 INSERT
7. 返回 202 + `{accepted: N, rejected: [...]}`；SDK 部分失败指数退避重试

SDK 三条铁律：不阻塞业务线程 · 不抛异常 · HTTP 失败 N 次降级本地文件，下个周期 replay。

### 查询（6 步）
1. Claude Code → `GET /api/v1/logs/search?...` + `Authorization: Bearer <read-key>`
2. AuthFilter 校验 key + scope=read
3. QueryController 校验参数；未传 appCode 时必须有上下文 appId（避免盲扫）
4. QueryService 构建 LogSearchCriteria（游标解码：base64 → server_ts + id）
5. LogMapper search 走 `idx_logs_app_level_ts`，`WHERE app_id=? AND level=? AND (server_ts,id)<(?,?) ORDER BY server_ts DESC, id DESC LIMIT ?`
6. 返回 `ResponseResult<Page<LogView>>` + `nextCursor`

## 跨仓协作约定

- **契约边界**：log-platform 定义 REST API 版本化契约；mcp-platform 的 `mcp-log-viewer` 消费该契约
- **版本化**：默认 `/api/v1`；breaking change 新起 `/api/v2`
- **契约文档**：OpenAPI 或 Markdown，位置候选 `docs/api/`（detail-design 定）
- **REST 契约变更流程**：任何变更先在 log-platform PR 中明确 "是否 breaking"；若是 breaking，必须同步在 mcp-platform 侧升级 mcp-log-viewer
- **E2E 验收**：端到端测试需启动两个服务（log-web + mcp-platform/server）

## 备选方案归档（brainstorming 期间评估过）

### 模块划分候选

| # | 方案 | 结论 | 原因 |
|---|---|---|---|
| 1 | 最小分层（3 module：core/web/sdk） | 不选 | log-core 会越长越大，扩展告警/订阅要塞进去 |
| 2 | 按功能域切（6 module） | **选** | 单一职责；天然对齐 areas.yaml；扩展只加不改 |
| 3 | Clean Architecture 分层（6 module） | 不选 | 对 MVP 过度；业务域仍混在同一层内 |

### MCP server 落位候选

| # | 方案 | 结论 | 原因 |
|---|---|---|---|
| 1 | 本仓 Java Maven module | 不选 | Java MCP SDK 最不成熟；部署模型与后端不匹配 |
| 2 | 本仓 `tools/log-mcp/` TS 子目录 | 不选 | 有现成 mcp-platform 可复用，无需自建基础设施 |
| 3 | 独立 Git 仓库 | 不选 | MVP 早期跨仓同步成本高 |
| 4 | **mcp-platform 的 packages/mcp-log-viewer** | **选** | 业界 C 模式 MCP Hub；其 README 已规划 mcp-log-viewer；复用鉴权/审计/Docker |
| 5 | MVP 不做 MCP，只给 REST | 不选 | 削弱 "agent-first" 定位；反馈回路不丝滑 |

### Agent 场景 MVP 选择

| # | 场景 | 结论 | 原因 |
|---|---|---|---|
| A | 本地 AI 编码助手 | **MVP 选** | 价值闭环最短；用户自己即首批使用者 |
| B | CI/CD 诊断 agent | 二期 | 需要"查日志"能力，复用 A 成果 |
| C | 灰度守护 agent | 二期 | 需要 stream/subscribe，扩展能力 |
| D | 生产自愈 agent | 远期 | 需要反向控制面，与"数据面定位"冲突最大 |
| E | 平台内置 AI | 不做 | 与"数据面专注"定位直接冲突 |

## Roadmap（二期候选 module / 能力）

| 能力 | 新增 module | 触发条件 |
|---|---|---|
| 告警规则 + 通知（钉钉等） | `log-alert` + `log-notification` | 运营/运维侧提需 |
| 长连接订阅（SSE/WS） | `log-subscription` | 场景 C（灰度守护）启动 |
| 其他语言 SDK | `log-sdk-py` / `log-sdk-go` 等 | 用户侧非 Java 应用接入 |
| 日志归档/分区 | 无新 module，改 logs 表 + 脚本 | 日志量超百万/日 |
| 管理后台 UI | `log-admin-ui`（或独立前端仓） | 用户规模扩大 |
