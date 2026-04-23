## 变更摘要

<!-- 1-2 句说清「做了什么 + 为什么」-->

## 影响范围

<!-- 涉及的 module / 接口 / 表 / 配置 -->

## 验证方式

<!-- 如何在本地或 CI 验证；附测试用例或截图 -->

## 风险与回滚

<!-- 已识别的风险点；回滚命令或步骤 -->

## REST 契约变更

请勾选：

- [ ] 不涉及 REST 契约
- [ ] 仅新增字段（向前兼容）→ 已同步更新 `docs/api/v1/openapi.yaml`
- [ ] **Breaking change** → 已在 `mcp-platform` 仓同步创建 issue / PR：<填写链接>

> log-platform REST 契约由 `docs/api/v1/openapi.yaml` 维护，下游 `mcp-platform/packages/mcp-log-viewer` 依赖此契约。
> Breaking change 必须在 mcp-platform 侧同步处理，详见 `requirements/REQ-2026-001/artifacts/detailed-design.md` §10.1。
