---
feature_id: F-02-skeleton-6modules
title: 建立 6 module 父子 pom 骨架
status: done
type: feature
module: skeleton
effort_pd: 1
depends_on: [F-01-archive-skeleton]
refs:
  - artifacts/outline-design.md#22-6-module-划分
  - artifacts/outline-design.md#23-包命名规范
  - artifacts/outline-design.md#52-退役步骤拆为-2-个独立-commit
---

## 背景

按 outline-design §2.2 / §2.3 建 6 个空 module + 父子 pom。本 feature 仅落骨架，不写业务代码。

## 实施步骤

1. 在根目录新建：`log-common` / `log-source` / `log-ingestion` / `log-query` / `log-web` / `log-sdk`
2. 每个 module 写最小 `pom.xml`：仅 `<artifactId>` / `<parent>` / 必要 `<dependencies>`（按 §2.2 表的依赖列）
3. 创建标准包目录：
   - `src/main/java/com/hj/log/<module-prefix>/.gitkeep`
   - `src/test/java/com/hj/log/<module-prefix>/.gitkeep`
   - `src/main/resources/.gitkeep`（log-common 还需 `db/` 子目录）
4. 根 `pom.xml` 的 `<modules>` 按依赖顺序注册：
   ```xml
   <modules>
     <module>log-common</module>
     <module>log-source</module>
     <module>log-ingestion</module>
     <module>log-query</module>
     <module>log-web</module>
     <module>log-sdk</module>
   </modules>
   ```
5. commit 信息：`feat(skeleton): 建立 6 module 父子 pom 骨架`

## 验收标准

- [ ] 6 个新 module 目录存在，含最小 `pom.xml`
- [ ] 根 `pom.xml` `<modules>` 注册 6 个 module 且顺序合理
- [ ] `mvn -q -DskipTests clean compile` 通过
- [ ] 每个 module 包结构与 outline-design §2.3 命名规范一致（`com.hj.log.<module-prefix>`）
- [ ] log-sdk 不依赖任何其他 module；log-web 依赖 source/ingestion/query 三个
