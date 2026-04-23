---
id: REQ-2026-001
title: 日志平台骨架模块 · 技术可行性评估
created_at: 2026-04-23T09:45:00Z
phase: tech-research
refs-requirement: true
---

# REQ-2026-001 · 技术可行性评估

## 结论

**feasibility: high**

核心依据：

1. 技术栈（JDK 21 + Spring Boot 3.x + MyBatis + MySQL 8）完全在团队既有约束内（来源：context/project/log-platform/CLAUDE.md:55），根 `pom.xml` 已锁定版本，无新依赖引入风险。
2. 规模数字（10 万条/日 · 单条 ≤ 2KB · QPS 500 · 保留 7 天 ≈ 1.4GB）对单库 MySQL 完全可承载；`notes.md:63` 的「logs 单表不分区、二期按 server_ts 月分区」决策有充分依据。
3. 6 module 划分（log-sdk / log-common / log-source / log-ingestion / log-query / log-web）依赖无循环，log-sdk 独立发布无框架绑定，模块边界干净（来源：../notes.md:14）。
4. 唯一集中复杂度在 `log-sdk` 的异步缓冲 + 失败降级机制，属于有明确方案的工程问题，不构成架构级阻碍。
5. 无 blocker。

## 风险评估

### R1 · 既有骨架退役策略模糊 · tech · likelihood=high · impact=medium

- **描述**：需求已明确「不复用」既有 `log-core` / `log-web`（来源：requirement.md:14），但两个模块仍注册在根 `pom.xml` `<modules>` 中，4 个 DO（`LogEntry` / `AlertRule` / `LogSource` / `NotificationConfig`）仍在 `log-core` 包树下。新 6 module 里的 `log-web` 与骨架 `log-web` 同名，直接删旧建新会产生大面积 rename/delete diff，`git log --follow` 追溯断裂。骨架后续通过 `git fetch upstream` 同步时 `log-core` / `log-web` 不在同步边界内（context/team/onboarding/syncing-from-skeleton.md 定义同步边界为 `.claude/` 和 `context/team/`），本地覆写风险可控但仍需一次性干净退役。
- **缓解**：在进入 outline-design 前，用一个独立 `chore(skeleton): 退役旧骨架模块` commit 清理：
  1. 从根 `pom.xml` 的 `<modules>` 删除 `log-core` 和 `log-web` 条目
  2. `git mv log-core _archive/log-core-skeleton && git mv log-web _archive/log-web-skeleton`，保留 Git 历史引用
  3. 新 6 module 目录全量新建，在 PR 描述里明确「新 log-web 有意替换骨架 log-web」
  4. 整个退役动作单独成 commit，功能开发分离，便于 revert

### R2 · SDK 异步缓冲 + 失败降级复杂度超标 · tech · likelihood=medium · impact=medium

- **描述**：log-sdk 需实现 5 个子机制：① 内存环形缓冲（非阻塞）② 后台 flush 线程（定时或满 100 条）③ HTTP 失败指数退避重试 ④ 重试耗尽降级本地文件 ⑤ 下周期从本地文件 replay。三条铁律「不阻塞 / 不抛异常 / 失败降级」见 requirement.md:64 和 notes.md:121。五个机制并发交互（缓冲区满、文件锁、JVM 关闭钩子）容易引入竞态；`notes.md:108` 对 HTTP client 只给候选（OkHttp 或 JDK 原生），未最终敲定，detail-design 期间接口仍可能变动。
- **缓解**：detail-design 阶段锁定如下实现：
  - HTTP client 固定 JDK 原生 `java.net.http.HttpClient`（零新增依赖，JDK 21 已稳定）
  - 缓冲用 `LinkedBlockingDeque` 固定容量 1024；flush 触发：满 100 条 **OR** 每 2 秒
  - 降级文件路径通过 SDK 配置项 `logSdkFallbackDir` 注入，默认 `${user.home}/.log-sdk/fallback/`
  - replay 线程与 flush 线程分离，避免死锁
  - SDK 单独集成测试用 MockWebServer 覆盖：正常 flush / 重试耗尽写文件 / JVM shutdown hook 强制 flush
- SDK 单独 4 人日（占总开发量约 30%），在工作量估算的 `dev_detail` 中单独拆行。

### R3 · logs 单表 TEXT 字段存储与查询成本 · tech · likelihood=medium · impact=medium

- **描述**：10 万条/日 × 2KB ≈ 200MB/日 × 7 天 ≈ 1.4GB，对 InnoDB 单表无压力，但：
  - TEXT 字段（`message` / `stack_trace` / `context_data`）存储在页外（off-page），增加 B-tree 扫描 IO 跳转
  - `GET /logs/search` 的 keyword 若用 `LIKE '%xxx%'` 会全表扫描，与 P99 ≤ 100ms 冲突
  - 保留期 DELETE（Spring Scheduled + `DELETE WHERE server_ts < ?`）大事务会 undo log 膨胀
- **缓解**：
  - MVP 阶段 keyword 限定为前缀匹配或精确子串（`LIKE 'xxx%'`），禁止裸 `LIKE '%xxx%'`；在 API 文档中明确此限制，同步告知 mcp-log-viewer
  - 保留期 DELETE 改分批：每次 500 行 + `SLEEP(10ms)` 循环，封装为 `LogRetentionTask`（Spring `@Scheduled` 每小时触发）
  - DDL 脚本注释中预留「按 server_ts 月分区」二期改造入口（来源：../notes.md:63）

### R4 · write QPS 500 下 api_keys.last_used_at 高频 UPDATE · tech · likelihood=low · impact=medium

- **描述**：批量 INSERT（notes.md:119「单 SQL 多值 INSERT」）在 QPS 500 / 批 100 条下实际 5 次/秒批量 SQL，无压力。但 `api_keys.last_used_at` 每次写入都 UPDATE（notes.md:68）意味着 AuthFilter 每请求一次 UPDATE：10 应用 × 500 QPS = 500 UPDATE/s。InnoDB 行锁不同 `app_id` 不同行无争用，但 redo log 写入 500 次/秒，在低配盘场景可能成瓶颈。
- **缓解**：MVP 按 notes.md:68 决策同步 UPDATE，但 AuthFilter 内加时间窗口节流——用 Caffeine 本地缓存记录每个 `key_hash` 的最近更新时间，TTL 60s、容量 100 条；若 < 60s 前已更新则跳过。稳定写入场景下 UPDATE 频率从 500/s 降到最多 10/分钟（10 应用 × 1 次/60s）。0 外部依赖。detail-design 记录此实现并注明「二期改为异步合并更新」。

### R5 · API Key 遗失处理流程缺失 · security · likelihood=medium · impact=medium

- **描述**：`api_keys.key_hash` 只存 SHA-256，明文仅首次返回（requirement.md:69-70），密钥遗失后无法找回只能重签；但 `GET /apps/{id}/keys` 只返回 `key_prefix`（前 8 位）和 `scope`，多 key 同类型时辨识度极低，管理员难以判断吊销哪个 key。另外 `LOG_PLATFORM_ADMIN_TOKEN` 明文环境变量有泄露风险（CI 日志、`docker inspect`）。
- **缓解**：
  - `api_keys` 表 MVP 阶段即加 `label` 列（VARCHAR(64)，可选，调用方自定义描述如 `order-service-write-prod`）
  - 签发响应体返回 `keyPrefix + label + scope`；列表接口返回 `keyPrefix + label + scope + status + createdAt`，管理员据此辨识
  - `LOG_PLATFORM_ADMIN_TOKEN` 在启动文档明确要求 Docker secret 或 `.env`（已 gitignore）注入，禁止硬编码进 `application.yml`
  - `GlobalExceptionHandler` 对外响应脱敏任何包含 token 的异常信息（符合全局 CLAUDE.md §9）

### R6 · 跨仓契约同步无机制保障 · business · likelihood=low · impact=high

- **描述**：REST 契约由 log-platform 维护，mcp-platform 的 `mcp-log-viewer` 消费（notes.md:133-137）。当前约定「breaking change 新起 `/api/v2`」但无通知机制——本仓 PR 合入后 mcp-log-viewer 可能未同步升级，导致 Claude Code 查询失败。MVP 期同队维护协同成本可控，但随需求演进风险上升。
- **缓解**：
  - 本仓 `.github/pull_request_template.md` 加 checkbox：`[ ] 是否包含 REST 契约变更？若是，请在 mcp-platform 仓库同步创建对应 issue/PR 链接`
  - 契约文档位置 detail-design 阶段确定为 `docs/api/v1/openapi.yaml`，在 notes.md 跨仓协作章节注明为 mcp-log-viewer 的唯一消费来源
  - 短期手工 checklist 保障；二期引入 contract testing（Pact 或快照测试）

### R7 · 项目 CLAUDE.md 与新架构脱节 · ops · likelihood=high · impact=medium

- **描述**：`context/project/log-platform/CLAUDE.md` 第 26-32 行「模块结构」、36-44 行「分层约定」、63-65 行「测试约定」、57 行 schema 路径、以及「告警规则核心字段」整段，均基于旧 `log-core` / `log-web` 两模块 + 告警功能的骨架假设。新 6 module + 不做告警的架构下这些内容全部过期。不更新则后续 AI 编码助手（含本会话之外的 Agent）会基于过期分层生成错误代码，违反「文档即记忆」硬原则（来源：CLAUDE.md:33）。
- **缓解**：outline-design 完成、6 module 目录结构确定后立即更新 `context/project/log-platform/CLAUDE.md`：
  1. 模块结构章节替换为 6 module 依赖关系图
  2. 分层约定中补充各 module 对应的包前缀（`com.hj.log.common` / `com.hj.log.source` / …）
  3. 测试约定补充 log-sdk 的 MockWebServer 约定
  4. 删除「告警规则核心字段」章节（告警 MVP 不做）
- 作为独立 `docs: 同步 6 module 架构至项目 CLAUDE.md` commit 与功能代码分离。

## 工作量估算

| 类目 | 人日 | 拆分 |
|---|---:|---|
| 设计 | 7 | outline-design 2 · detail-design 后端 3 · detail-design SDK 1 · API 契约文档 1 |
| 开发 | 15 | 骨架退役 + 根 pom 6 module 注册 1 · log-common 1 · log-source 3 · log-ingestion 2 · log-query 3 · log-web 1 · log-sdk 4 |
| 测试 | 4 | Mapper H2 单测 1 · Web 层 MockMvc 1 · SDK MockWebServer 1 · E2E smoke + retention task 1 |
| **合计** | **26** | |

备注：估算基于「需求复杂度（6 module · 8 endpoint · 1 独立 SDK · 3 表）+ 团队对 Spring Boot/MyBatis 的成熟度」；不含上线前的部署脚本、压测、环境初始化等工作。

## 前置条件（上线前必须完成）

1. **旧骨架退役 commit**：从根 `pom.xml` 移除 `log-core` / `log-web` 注册，`git mv` 至 `_archive/`，确保 `mvn clean compile` 在 6 module 结构下通过
2. **三表 DDL 脚本**：`app_registrations` / `api_keys` / `logs` 在 detail-design 定稿并落位 `log-common/src/main/resources/db/schema.sql`（或各 module 自带 `resources/db/`）
3. **LOG_PLATFORM_ADMIN_TOKEN 初始化方案**：生成命令（建议 `openssl rand -hex 32`）+ `.env.example` 文件 + 注入方式说明
4. **`api_keys.label` 列决策**：detail-design 确认 MVP 阶段即加（建议加，成本极低，避免日后遗失密钥无法辨识）
5. **`context/project/log-platform/CLAUDE.md` 更新**：outline-design 完成后立即同步，保持 AI 辅助编码时的分层约定与新架构一致
6. **跨仓协作约定落地**：mcp-platform 侧确认 `mcp-log-viewer` 消费契约格式（OpenAPI YAML 优选）和路径（候选 `docs/api/v1/openapi.yaml`），detail-design 产出

## 阻碍项

无。所有已识别风险均有可执行缓解方案。

## 下一步建议

1. 确认本评估结论 → `/requirement:next` 推进到 **outline-design** 阶段
2. outline-design 阶段优先输出：① 6 module 的 Maven 父子 pom 骨架 ② 包命名规范 ③ 骨架退役方案的具体 commit 拆分 ④ 架构图（模块依赖 + 请求流）
3. 在 outline-design 结论出来后立即执行 R7 的 CLAUDE.md 同步，避免后续阶段引用过期文档
