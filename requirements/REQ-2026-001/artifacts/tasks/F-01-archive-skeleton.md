---
feature_id: F-01-archive-skeleton
title: 归档旧 log-core / log-web 骨架
status: done
type: chore
module: skeleton
effort_pd: 0.5
depends_on: []
refs:
  - artifacts/outline-design.md#52-退役步骤拆为-2-个独立-commit
  - artifacts/tech-feasibility.md  # R1
---

## 背景

REQ-2026-001 重新设计 6 module 架构，旧 `log-core` / `log-web` 不复用，需归档但保留 git 历史以便追溯。outline-design §5 已锁定方案。

## 实施步骤

1. `git mv log-core _archive/log-core-skeleton`
2. `git mv log-web _archive/log-web-skeleton`
3. 编辑根 `pom.xml`：从 `<modules>` 删除 `<module>log-core</module>` 与 `<module>log-web</module>`；保留 `dependencyManagement` 与 `properties`（被新 module 复用）
4. 新建 `_archive/README.md`：
   - 归档原因引用 `requirements/REQ-2026-001/artifacts/outline-design.md`
   - 给出 `git log --follow _archive/log-core-skeleton/<file>` 示例
5. commit 信息：`chore(skeleton): archive 旧 log-core / log-web 骨架`

## 验收标准

- [ ] `log-core` / `log-web` 目录已移至 `_archive/`
- [ ] 根 `pom.xml` `<modules>` 段为空
- [ ] `mvn -q -N validate` 退出码 0
- [ ] `_archive/README.md` 含归档原因与 git 历史追溯命令
- [ ] commit 单一职责，diff 仅含 git mv + pom 编辑 + README 新增
