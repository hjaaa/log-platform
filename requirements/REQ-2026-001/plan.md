# REQ-2026-001 · 日志平台骨架模块

## 目标

交付一个 **agent-first** 的分布式日志平台 MVP：让本地 AI 编码助手（Claude Code 经 mcp-platform/mcp-log-viewer）标准化读取应用日志，构建「看错误 → 改代码 → 验证」的反馈回路；不复用仓库既有 `log-core`/`log-web` 骨架，从零设计 6 个 Maven module + 3 张表 + 8 个 REST endpoint + 1 个独立 Java SDK。

## 范围

### 包含（MVP 交付物）

- 后端服务：6 个 Maven module（`log-sdk` · `log-common` · `log-source` · `log-ingestion` · `log-query` · `log-web`），启动一个 Spring Boot 进程（详见 `notes.md` §模块划分）
- 数据库：3 张表（`app_registrations` · `api_keys` · `logs`），MySQL 生产 / H2 测试
- REST API：8 个 endpoint，版本化前缀 `/api/v1`（admin 4 · write 1 · read 3）
- Java SDK：异步缓冲 + 批量 HTTP POST + 失败降级本地文件；非阻塞、不抛异常
- REST API 契约文档（OpenAPI 优选）：交付给 mcp-platform 的 `mcp-log-viewer` 消费
- 骨架退役：旧 `log-core`/`log-web` 归档至 `_archive/`、根 pom.xml 改为 6 module 注册

### 不包含（明确不做）

- 告警规则、通知渠道、相关数据表 — 二期
- 长连接订阅（SSE / WebSocket） — 二期
- 平台内置 AI 能力（归因 / 摘要 / 问答） — 与"数据面专注"定位冲突
- MCP server 实现 — 由 mcp-platform 仓独立需求交付（`packages/mcp-log-viewer`）
- 生产运行时自愈能力（反向控制面）
- 多语言 SDK（Python / Node / Go） — 二期
- 日志文件 tail / stdout 采集 / Sidecar — 所有日志走 SDK HTTP POST
- 多租户 RBAC、Admin UI、平台端脱敏

详细决策与备选方案归档见 `notes.md` §备选方案归档。

## 里程碑

估算基准：2026-04-23 进入 tech-research，合计 26 人日（design 7 · dev 15 · test 4）。实际排期受并行度影响，下表用「相对工作日 D+N」表示：

| 阶段 | 状态 | 相对工作日 | 关键交付物 |
|---|---|---|---|
| definition | ✅ 已完成（2026-04-23） | D0 | `artifacts/requirement.md` + `notes.md` |
| tech-research | ✅ 已完成（2026-04-23） | D0 | `artifacts/tech-feasibility.md` |
| outline-design | 待开始 | D+2 | 6 module 父子 pom 骨架 + 包命名规范 + 架构图 + 骨架退役 commit 拆分 |
| detail-design | 待开始 | D+7 | `artifacts/detailed-design.md` + `features.json` + 3 表 DDL + OpenAPI 契约 |
| task-planning | 待开始 | D+8 | `artifacts/tasks/*.md`（每 feature 一个） |
| development | 待开始 | D+23 | 代码落盘 + 每 feature 有 `/code-review` 报告 |
| testing | 待开始 | D+27 | `artifacts/test-report.md` + 追溯链报告 |

## 风险

风险清单（从 `artifacts/tech-feasibility.md` 归并，详见原文各风险节）：

- **R1 · 骨架退役策略模糊**（tech · high/medium）：旧 `log-core`/`log-web` 仍注册在根 pom 且新 `log-web` 同名。**应对**：独立 `chore(skeleton): 退役旧骨架模块` commit，`git mv` 至 `_archive/` + 删 pom 注册，与功能开发分离便于 revert。进入 outline-design 前执行。
- **R2 · SDK 异步缓冲复杂度**（tech · medium/medium）：5 个子机制（缓冲/flush/重试/降级/replay）并发交互易引入竞态。**应对**：detail-design 锁定 JDK 原生 `HttpClient` + `LinkedBlockingDeque(1024)` + 满 100 或 2s flush；MockWebServer 覆盖正常/失败/shutdown 路径。SDK 单独 4 人日。
- **R3 · logs 单表 TEXT + keyword 查询成本**（tech · medium/medium）：`LIKE '%xxx%'` 全表扫与 P99 100ms 冲突；保留期 DELETE 大事务膨胀 undo。**应对**：keyword 限前缀匹配（`LIKE 'xxx%'`）；DELETE 分批 500 行 + SLEEP；DDL 注释预留按月分区的二期入口。
- **R4 · api_keys.last_used_at 高频 UPDATE**（tech · low/medium）：500 UPDATE/s 在低配盘可能成瓶颈。**应对**：AuthFilter 内用 Caffeine（容量 100、TTL 60s）节流，UPDATE 频率降至 ≤ 10 次/分钟；0 外部依赖；二期改异步合并。
- **R5 · API Key 遗失难辨识 + admin token 泄露**（security · medium/medium）：只返回 `key_prefix` 不足以辨识。**应对**：`api_keys` 表 MVP 即加 `label` 列；`LOG_PLATFORM_ADMIN_TOKEN` 通过 Docker secret/.env 注入，禁止 `application.yml` 硬编码；`GlobalExceptionHandler` 对 token 脱敏。
- **R6 · 跨仓契约同步无机制**（business · low/high）：breaking 变更 mcp-log-viewer 可能漏同步。**应对**：PR 模板加 checkbox 手工把关；契约文档固定在 `docs/api/v1/openapi.yaml`；二期引入 contract testing（Pact 或快照）。
- **R7 · 项目 CLAUDE.md 与新架构脱节**（ops · high/medium）：仍描述旧 2 module + 告警字段，会误导后续 Agent。**应对**：outline-design 完成后立即更新 `context/project/log-platform/CLAUDE.md`（模块结构 + 分层约定 + 测试约定 + 删告警章节），作为独立 `docs:` commit。

## 前置条件（上线前必须完成）

1. 骨架退役 commit（R1 缓解动作），`mvn clean compile` 在 6 module 结构下通过
2. 3 表 DDL 脚本落位 `log-common/src/main/resources/db/schema.sql`
3. `LOG_PLATFORM_ADMIN_TOKEN` 生成命令 + `.env.example` + 注入方式文档
4. `api_keys.label` 列在 MVP 即加入（成本极低）
5. `context/project/log-platform/CLAUDE.md` 同步新架构
6. 与 mcp-platform 侧确认 `mcp-log-viewer` 消费契约的格式（OpenAPI YAML）与路径
