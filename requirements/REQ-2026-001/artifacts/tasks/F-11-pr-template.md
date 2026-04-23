---
feature_id: F-11-pr-template
title: 跨仓 PR 模板
status: done
type: chore
module: ops
effort_pd: 0.25
depends_on: []
refs:
  - artifacts/detailed-design.md#101-跨仓-pr-模板落-githubpull_request_templatemd
---

## 实施清单

- 路径：`.github/pull_request_template.md`
- 内容按 detail-design §10.1 模板，含三段：
  - 变更摘要 / 影响范围 / 验证方式 / 风险与回滚（基础四段）
  - REST 契约变更 checkbox：
    - [ ] 不涉及契约
    - [ ] 仅新增字段（向前兼容）→ 同步 `docs/api/v1/openapi.yaml`
    - [ ] Breaking change → 在 mcp-platform 仓同步 issue/PR：<URL>

## 验收标准

- [ ] `.github/pull_request_template.md` 存在
- [ ] 含 REST 契约变更三个分支选项
- [ ] 新建 PR 时模板自动加载（创建一个空 PR 验证）
