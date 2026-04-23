---
feature_id: F-13-claude-md-sync
title: 同步项目 CLAUDE.md 到 6 module 架构
status: done
type: doc
module: doc
effort_pd: 0.5
depends_on: [F-02-skeleton-6modules]
refs:
  - artifacts/outline-design.md#6-与既有规范文档的同步项r7-缓解
  - artifacts/tech-feasibility.md  # R7
---

## 背景

R7 缓解：旧 CLAUDE.md 描述 2 module + 告警字段，新架构上线后会误导 Agent 生成错误代码。**必须独立 `docs:` commit**，与代码 commit 分离。

## 实施清单

文件：`context/project/log-platform/CLAUDE.md`

### 替换段

| 章节 | 操作 |
|---|---|
| 「构建与运行」 | `mvn -pl log-core test` 改为按 module 列出（log-common/log-source/log-ingestion/log-query/log-web/log-sdk） |
| 「模块结构」 | 替换为 outline-design §2.2 的 6 module + 依赖图 |
| 「分层约定」 | 替换为 outline-design §2.3 的 6 个包前缀映射表 |
| 「测试约定」 | 增加 log-sdk 用 MockWebServer 的约定 |
| schema 路径 | 改为 `log-common/src/main/resources/db/schema.sql` |

### 删除段

- 「告警规则核心字段」整段（MVP 不做告警）

### 新增段

- 「配置项命名空间」小节（参照 outline-design §2.4 + detail-design §7）：
  - 服务端 `log.platform.*`
  - SDK `log.sdk.*`

## 验收标准

- [ ] 模块结构段反映 6 module 依赖图
- [ ] 分层约定补 6 个包前缀映射
- [ ] schema 路径改为 `log-common/src/main/resources/db/schema.sql`
- [ ] 告警规则核心字段章节已删除（grep 无 `matchPattern` / `coolDownPeriod`）
- [ ] 新增配置项命名空间小节，含两个前缀
- [ ] 该改动是单独 `docs:` commit，与 F-02 / F-03 等代码 commit 分离
- [ ] 改动后人工通读一遍，无遗留旧 module 名（除 `_archive` 上下文外）
