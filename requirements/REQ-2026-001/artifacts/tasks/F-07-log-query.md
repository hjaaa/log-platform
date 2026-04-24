---
feature_id: F-07-log-query
title: log-query · search / get / trace 三个读端点
status: done
type: feature
module: log-query
effort_pd: 3
depends_on: [F-04-log-source-auth, F-06-log-ingestion]
created_at: 2026-04-23T11:45:00Z
updated_at: 2026-04-24T10:55:00Z
retroactive_note: |
  commit a2af813（29 tests green）状态漂移补齐。design-consistency-checker
  追溯审查通过（issues: []），覆盖：三端点 path/method、游标格式 §2.4.1、
  keyword % 前缀拒绝、pageSize/limit 上限、QUERY_* 6 个错误码触发路径、
  search DESC / trace ASC 排序、翻页条件 (server_ts, id) &lt; (?, ?)。
  task 状态由 in-progress 补齐为 done。索引覆盖（非 F-07 scope）已在
  schema.sql 建（idx_logs_app_ts / idx_logs_trace），留待 F-14 E2E 验证。
refs:
  - artifacts/detailed-design.md#24-查询面read-scope3-个
  - artifacts/review-20260424-023911.md（F-10 评审中交叉验证了 LogQueryController）
---

## 背景

agent / mcp-log-viewer / 人工查询的入口。游标分页避免 offset 深扫；keyword 严格限制前缀匹配防全表扫。

## 实施清单

### Controller

- `com.hj.log.query.controller.LogQueryController`
  - `GET /api/v1/logs/search`
  - `GET /api/v1/logs/{id}`
  - `GET /api/v1/logs/trace/{traceId}`

### DTO / Cursor

- `LogSearchCriteria`：从 query string 反序列化
- `LogView`：精简对外字段（appCode 通过 join 或缓存填充）
- `Page<T> { items, nextCursor, pageSize }`
- `com.hj.log.query.cursor.CursorCodec`：
  - encode：`base64(serverTsMillis + ":" + id)`
  - decode：解失败 → `BusinessException("QUERY_INVALID_CURSOR", 400)`

### 校验

- `appCode` 与 `traceId` 至少传一个，否则 `QUERY_MISSING_APP_FILTER`
- `pageSize ≤ log.platform.query.max-page-size`，否则 `QUERY_PAGE_SIZE_TOO_LARGE`
- `keyword` 不允许以 `%` 开头，否则 `QUERY_KEYWORD_PATTERN_INVALID`
- `/trace/{traceId}` 的 `limit ≤ trace-max-limit`，否则 `QUERY_LIMIT_TOO_LARGE`

### Mapper

- `LogQueryMapper.search(criteria)` SQL 走 `idx_logs_app_level_ts` / `idx_logs_app_ts`
- `LogQueryMapper.findById(id)`
- `LogQueryMapper.searchByTrace(traceId, limit)` 走 `idx_logs_trace`，`ORDER BY server_ts ASC`

### 字段裁剪

- 解析 `fields=a,b,c` → 用 `Jackson` `@JsonView` 或 `MappingJacksonValue` 动态裁剪
- 默认全字段返回

## 验收标准

- [ ] `/search` 通过 MockMvc 集成测试覆盖：
  - appCode 过滤
  - 多 level（`level=ERROR,WARN`）
  - 游标翻页（连查 3 页结果不重不漏）
  - `fields` 裁剪（响应仅包含指定字段）
  - 缺过滤报 `QUERY_MISSING_APP_FILTER`
- [ ] `/logs/{id}` 不存在返回 `QUERY_LOG_NOT_FOUND` (404)
- [ ] `/trace/{id}` `truncated` 字段在超 limit 时为 `true`
- [ ] 游标格式与 detailed-design §2.4.1 描述一致；非法游标返回 `QUERY_INVALID_CURSOR`
- [ ] `/search` keyword 以 `%` 开头时返回 `QUERY_KEYWORD_PATTERN_INVALID`
- [ ] `mvn -pl log-query test` 全绿
