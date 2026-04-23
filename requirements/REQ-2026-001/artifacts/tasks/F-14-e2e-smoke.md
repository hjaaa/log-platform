---
feature_id: F-14-e2e-smoke
title: E2E smoke 测试 + 追溯链报告
status: pending
type: test
module: test
effort_pd: 1
depends_on: [F-08-log-web, F-09-log-sdk]
refs:
  - artifacts/detailed-design.md#9-时序图
  - artifacts/outline-design.md  # 全文
---

## 背景

testing 阶段 `artifacts/test-report.md` 与追溯链报告的实际产出物。E2E 验证写入→查询→trace 完整链路，覆盖 outline-design §3 的 W1–W7 与 Q1–Q6。

## 实施清单

### Smoke 测试脚本

- 路径：`scripts/smoke-test.sh`
- 步骤：
  1. `docker compose -f scripts/docker-compose.test.yml up -d mysql`
  2. 等 MySQL 就绪 → 跑 `schema.sql`
  3. 启动 log-web（指定 test profile）
  4. `curl POST /apps` 注册 `e2e-test-app`
  5. `curl POST /apps/{id}/keys` 签发 write key + read key
  6. 用 SDK 写 5 条日志（含 traceId=`smoke-trace-001`）
  7. `flushNow` + 等 1s
  8. `curl GET /logs/search?appCode=e2e-test-app&level=ERROR` 期望返回 ≥ 1
  9. `curl GET /logs/trace/smoke-trace-001` 期望返回 5 条
  10. `curl DELETE /apps/{id}/keys/{keyId}` 撤销 → 用旧 key 再写期望 401
  11. `docker compose down`

### 追溯链报告

- 路径：`artifacts/test-report.md`
- 包含三段：
  - 测试结果摘要（pass/fail count）
  - 追溯矩阵：requirement.md 段落 ↔ feature_id ↔ 代码包路径 ↔ 测试类
  - 性能记录（写入 P99 / 查询 P99）

## 验收标准

- [ ] smoke 脚本可在 CI 一键跑（含 MySQL container 启动 / 拆除）
- [ ] 步骤 4-10 全部 200 / 401（按预期）
- [ ] 测试覆盖：注册 → 签发 → 写 → search → trace → 撤销→ 401
- [ ] 追溯矩阵覆盖 14 个 feature 全集
- [ ] 报告落 `artifacts/test-report.md`
- [ ] `traceability-gate-checker` Skill PASS
