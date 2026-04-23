# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

本仓库是基于 [agentic-meta-engineering](https://github.com/hjaaa/agentic-meta-engineering) 骨架派生的 **log-platform**（分布式日志平台）下游项目。上手见 `context/team/onboarding/agentic-engineer-guide.md`。

## 仓库布局

- `.claude/` — Commands / Skills / Agents / Hooks 定义（工具层，继承自骨架，不在此仓库直接修改）
- `context/team/` — 团队通用知识（继承自骨架）
- `context/project/log-platform/` — 本项目专属知识（技术规范、业务域、长期决策）
- `requirements/<REQ-ID>/` — 单个需求的全周期产出

## 项目技术规范

@context/project/log-platform/CLAUDE.md

## 常用入口

| 想做什么 | 用什么 |
|---|---|
| 开一个新需求 | `/requirement:new <标题>` |
| 恢复之前的需求 | `/requirement:continue` |
| 看当前阶段/进度 | `/requirement:status` |
| 做代码审查 | `/code-review` |
| 提 PR | `/requirement:submit` |
| 卡住了/不确定 | `/agentic:help` |

全量命令见 `.claude/commands/`；详细 SOP 见 `context/team/onboarding/agentic-engineer-guide.md`。

## 自动机制（Hook）

- `protect-branch` — `main/master/develop` 上直接做 Edit/Write 会被阻断；改代码前先切 feature 分支
- `auto-progress-log` — 当前需求的 `process.txt` 由 Hook 自动追加，不要手工维护
- `stop-session-save` — 会话结束自动打 `SESSION_END` 标记，支撑 `/requirement:continue` 跨会话恢复

## 四条硬原则

1. **文档即记忆**：人和 AI 读同一份 Markdown
2. **位置即语义**：路径承载分类信息，不依赖元数据
3. **渐进式披露**：入口轻量，按需检索。禁止盲目 glob 全部 `context/`
4. **工具封装知识，不封装流程**

## 骨架同步

本仓库继承自骨架，如需同步骨架升级：

```bash
git remote add upstream git@github.com:hjaaa/agentic-meta-engineering.git
git fetch upstream
```

同步边界与流程见 `context/team/onboarding/syncing-from-skeleton.md`。
**不要直接修改 `.claude/commands/`、`.claude/skills/`、`.claude/agents/`、`.claude/hooks/`、`context/team/`**，这些路径由骨架管理。

## 和 AI 协作的基本法

@context/team/ai-collaboration.md

## 团队规范

- Git：@context/team/git-workflow.md
- 工具链：@context/team/tool-chain.md
