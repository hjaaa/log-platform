---
id: REQ-2026-001
title: 日志平台骨架模块
created_at: 2026-04-23T08:00:00Z
refs-requirement: true
---

# REQ-2026-001 · 日志平台骨架模块

## 背景

本项目的目标是从零设计一个分布式日志平台，定位为 **agent-first**——与传统日志系统（侧重人工运维与告警）不同，本平台的核心价值在于让 AI 编码助手（Claude Code 类）直接读取日志信息，构建"看错误 → 改代码 → 验证"的反馈回路，在开发阶段自主修复问题 [待用户确认]。

仓库已有骨架代码（来源：context/project/log-platform/CLAUDE.md:26）定义了 `log-core` / `log-web` 两个 Maven 模块，以及 `LogEntry / AlertRule / LogSource / NotificationConfig` 四个 DO。本需求**不复用**这些既有代码，而是基于重新评估后的 MVP 定位，从零设计顶层模块划分、数据模型、接口契约 [待用户确认]。

definition 阶段（本文档）聚焦 WHY / WHAT；HOW 层面的产出（模块详细分工、表结构、接口契约初稿、跨仓协作约定）记录在 `../notes.md`，供后续 outline-design / detail-design 阶段消费。

## 目标

- **主目标**：让本地 AI 编码助手（MVP 锁定 Claude Code，通过 `mcp-platform` 仓库另行开发的 `mcp-log-viewer` package 接入）通过标准化通道读取任意接入应用的日志，支撑"agent 自主修 bug"的反馈回路 [待用户确认]。
- **次要目标**：人也能通过 HTTP 查询日志，但人不是 MVP 的主要服务对象 [待用户确认]。
- **定位边界**：本平台只提供日志数据面（收 + 存 + 给），不内置 AI 归因 / 自动摘要 / 智能问答等能力，分析动作由 agent 自身完成 [待用户确认]。

## 用户场景

### 场景 1（MVP · 必做）· 本地 AI 编码助手读日志修 bug

- **角色**：开发者 · Claude Code（或其他 MCP 兼容 IDE agent）· 业务应用（Java，集成 log-sdk）。
- **前置**：
  - 业务应用启动时注入 write 类 API Key；
  - Claude Code 通过 mcp-platform 的 HTTP 聚合入口接入，mcp-platform 内部持有 log-platform 的 read 类 API Key；
  - log-platform 已启动并可写可查。
- **主流程**：
  1. 开发者在 IDE 跑 `mvn test` 或启动应用 → 业务代码报错 → log-sdk 把日志（含 stack trace、traceId）异步 HTTP POST 到 log-platform。
  2. 开发者对 Claude Code 说"查一下 order-service 最近 X 分钟的 ERROR 日志"。
  3. Claude Code 调 `mcp-log-viewer` 的 MCP tool（如 `searchLogs`） → mcp-platform 反向调 log-platform 的 REST 查询 API → 返回匹配日志。
  4. Claude Code 基于 stack trace 与 traceId 关联分析 → 定位代码问题 → 建议/执行代码修改。
  5. 开发者 approve → 重跑验证 → 闭环完成。
- **期望结果**：开发者无需切日志面板手工翻；Claude Code 能在对话上下文内拿到足够信息定位并修复 bug。

### 场景 2-5（roadmap · 明确不做）

| 编号 | 场景 | 描述 | 归属 |
|---|---|---|---|
| B | CI/CD 诊断 agent | 测试/构建失败时 agent 自动拉关联日志定位根因 | 二期 |
| C | 灰度守护 agent | 灰度期间 agent 持续观测日志，发现问题主动 PR 修复 | 二期 |
| D | 生产运行时自愈 | 线上告警后 agent 执行 playbook | 远期 |
| E | 平台内置 AI 能力 | 平台端做归因/摘要/问答 | 不做（与本平台"数据面专注"定位冲突） |

上述场景保留扩展点（见"范围·不包含"说明），但 MVP 不交付。

## 非功能需求

下表数字为 brainstorming 期间依据 MVP 个人/小团队场景得出的**初始假设**，需在 tech-research 阶段压测或上线后按实际数据校正 [待用户确认]：

| 维度 | 指标 |
|---|---|
| 接入应用数 | ≤ 10 |
| 日志量 | ≤ 10 万条/日 |
| 单条日志大小 | ≤ 2KB（含 stack trace，超过由 SDK 截断） |
| 保留时长 | 7 天（MVP 硬编码） |
| 写入 QPS | 单机 ≥ 500 |
| 查询 P99 延迟 | ≤ 100ms |
| SDK 对业务线程 | 非阻塞；失败永不抛出 |

- **兼容性**：JDK 21、Spring Boot 3.x、MySQL 8+（与仓库约定对齐，来源：context/project/log-platform/CLAUDE.md:55）。
- **安全**：
  - API Key 只存 SHA-256 hash；签发时仅在一次响应中返回明文。
  - scope 分离：`write` 与 `read` 两种 scope 独立签发；业务应用用 write，agent 用 read。
  - 日志敏感信息脱敏方案 [待用户确认]（MVP 默认不做，由使用方自律）。
- **可观测性**：Spring Actuator `/health`，Micrometer 埋点写入/查询 QPS、错误率。

## 范围

### 包含（MVP 交付物）

- **后端服务**：6 个 Maven module 的 log-platform，启动一个 Spring Boot 进程。模块及依赖关系详见 `../notes.md` 的"模块划分"章节。
- **REST API**：8 个 endpoint。
  - 控制面（admin scope）：`POST /apps`、`GET /apps`、`POST /apps/{id}/keys`、`DELETE /apps/{id}/keys/{keyId}`。
  - 写入（write scope）：`POST /logs`（批量接收，单批条数在 detail-design 阶段敲定）。
  - 查询（read scope）：`GET /logs/search`、`GET /logs/{id}`、`GET /logs/trace/{traceId}`。
- **Java SDK**（`log-sdk` jar）：供业务应用集成；内部异步缓冲 + 批量 POST + 失败降级本地文件。
- **REST API 契约文档**：版本化路径前缀 `/api/v1`；文档格式 OpenAPI 或 Markdown，作为交付物供 mcp-platform 的 `mcp-log-viewer` 消费 [待用户确认]。

### 不包含（明确不做）

- **告警规则、通知（钉钉等）**：二期开坑；本 MVP 不做，也不预先创建告警相关数据表 [待用户确认]。
- **长连接订阅（SSE / WebSocket）**：二期。
- **平台内置 AI 能力**（归因、摘要、问答）：与"数据面专注"定位冲突，不做。
- **MCP server 实现**：不在本仓库；作为 mcp-platform 仓库的独立需求（计划中的 `packages/mcp-log-viewer`）交付。
- **生产运行时自愈能力**（反向控制面）：不做。
- **完整多租户 / RBAC 权限体系**：不做；MVP 单租户多应用即可。
- **其他语言 SDK**（Python / Node / Go）：二期；MVP 只交付 Java SDK。
- **日志文件 tail / stdout 采集 / Sidecar**：不做；所有日志走 SDK HTTP POST。

## 关键决策记录

| 决策点 | 选项 | 选择 | 依据 |
|---|---|---|---|
| MVP 场景 | A/B/C/D/E 五档 | **A（本地 AI 编码助手）** | 价值闭环最短；用户自己即首批使用者 |
| Agent 接入路径 | 裸 REST / REST+CLI / REST+MCP / 全做 | **REST + MCP（跨仓协作）** | mcp-platform 已有 Hub 基础设施；README 已规划 mcp-log-viewer |
| MCP server 落位 | 本仓 Java module / 本仓子目录 / 独立仓 / 不做 | **mcp-platform 的 packages/mcp-log-viewer（跨仓）** | 复用 mcp-platform 的聚合 gateway + 鉴权 + Docker 部署；业界 MCP Hub 模式 |
| 日志存储 | MySQL / Elasticsearch / ClickHouse | **MySQL 单库** | MVP 规模撑得住；避免运维复杂度 |
| 应用注册严谨度 | 轻（全局 key）/ 中（每 app 一 key）/ 重（含服务元信息） | **中** | 每应用独立 key，scope 分 write / read |
| 日志送入通道 | SDK POST / 文件 tail / stdout 采集 | **SDK HTTP POST** | 最简；避免 sidecar 复杂度 |

## 待澄清清单

1. **平台定位表述**：背景 / 目标章节中 "agent-first"、"反馈回路"、"不内置 AI" 等定性描述源自 2026-04-23 brainstorming 口述，需留档正式确认。
2. **既有代码不复用**：仓库已有 `log-core` / `log-web` 及 4 个 DO，本需求重新设计而不复用——与历史设计意图是否一致？
3. **规模与性能假设**：10 应用 / 10 万条/日 / 单条 2KB / 保留 7 天 / QPS 500 / P99 100ms 为初始假设，需 tech-research 阶段压测或上线后观测实际数据校正。
4. **日志敏感信息脱敏策略**：MVP 默认不做（使用方自律）。是否需要 SDK 侧提供脱敏 hook 作为二期能力？粒度如何？
5. **管理后台 UI**：控制面 MVP 只暴露 REST，不做 Web 界面。二期是否需要？
6. **admin token 初始化方式**：计划为 Spring 配置项 `LOG_PLATFORM_ADMIN_TOKEN`，支持环境变量 override。是否需要更强方案（Vault / 启动时交互式生成）？
7. **Claude Code ↔ mcp-platform ↔ log-platform 鉴权链**：mcp-platform 自带 API Key 校验；mcp-log-viewer 内部持 log-platform 的 read key。本机可信环境下 Claude Code ↔ mcp-platform 之间的 stdio 不额外鉴权。是否接受此分层？
8. **REST API 契约版本化策略**：初版路径前缀 `/api/v1`，breaking change 另起 `/api/v2`。是否需要 Sunset / Deprecation header 等正式机制？
9. **"不预留告警相关数据表"**：本 MVP 连告警所需的表都不建。若二期改动幅度大（加列 / 新表 / 回迁日志），是否接受此权衡？
10. **MCP 工具清单细节**：`searchLogs / getTrace / getLog` 三个工具的具体签名（入参校验、返回字段裁剪）归 mcp-platform 的需求；本需求只约束可被消费的 REST 端点。
