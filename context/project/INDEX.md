# 项目级知识索引

## 已入库项目

- [`log-platform/`](log-platform/INDEX.md) — 分布式日志平台（Java 21 + Spring Boot 3.3.4 + MyBatis）

## 知识沉淀说明

需求验收后通过 `/knowledge:extract-experience` 将 `requirements/<id>/notes.md` 关键决策转化为长期记忆，写入对应项目子目录。

新业务域需在 `context/project/log-platform/areas.yaml` 中先注册（kebab-case），再在 `meta.yaml.feature_area` 中使用。
