# `_archive/` · 已归档模块

本目录保存历史上注册过、但已被后续需求**整体取代**的模块；目录结构对应模块原位置，但 Git 历史保留以便追溯。

> 不在根 `pom.xml` `<modules>` 中，`mvn` 默认不会扫描；不在 `@SpringBootApplication.scanBasePackages` 范围内，运行期不会被装配。

## 当前归档清单

| 路径 | 原位置 | 归档需求 | 归档原因 |
|---|---|---|---|
| `log-core-skeleton/` | 仓库根 `log-core/` | [REQ-2026-001](../requirements/REQ-2026-001/artifacts/outline-design.md) | 重新设计 6 module 架构（log-common / log-source / log-ingestion / log-query / log-web / log-sdk），旧 `log-core` / `log-web` 不复用 |
| `log-web-skeleton/` | 仓库根 `log-web/` | 同上 | 同上；新 `log-web` 与旧同名但语义不同（仅启动入口 + 全局异常） |

## 追溯历史

`git log --follow` 仍可顺着 git mv 追溯文件历史：

```bash
git log --follow _archive/log-core-skeleton/src/main/java/com/hj/log/domain/LogEntry.java
git log --follow _archive/log-web-skeleton/src/main/java/com/hj/log/HealthController.java
```

## 何时清理

- 当确认归档模块在新架构下永不复用、且原始历史已通过 PR 合入主线 ≥ 2 个发版周期时，可整体 `git rm -r _archive/<module>`
- 清理动作必须独立 commit，避免与功能开发混淆
