---
feature_id: F-10-openapi-contract
title: OpenAPI v1 契约文件
status: in-progress
type: doc
module: doc
effort_pd: 1
depends_on: [F-05-log-source-ctrl, F-06-log-ingestion, F-07-log-query]
refs:
  - artifacts/detailed-design.md#2-rest-接口设计
  - artifacts/detailed-design.md#102-openapi-契约位置
---

## 背景

mcp-platform 的 `mcp-log-viewer` 通过此 YAML 消费契约。是跨仓协作的唯一权威来源。

## 实施清单

### 文件

- 路径：`docs/api/v1/openapi.yaml`
- OpenAPI 3.0.3
- 8 个 endpoint 全集（control plane 4 + write 1 + read 3）

### 结构

- `info`：title="log-platform API"、version=1.0.0、description（链至 detail-design）
- `servers`：localhost dev / 生产 placeholder
- `securitySchemes`：
  - `BearerAdmin`：`http bearer`
  - `BearerWriteKey`：`http bearer`
  - `BearerReadKey`：`http bearer`
- `components.schemas`：
  - `ResponseResult`（泛型用 `oneOf`/`allOf` 模式）
  - `LogEvent` / `LogView` / `IngestRequest` / `IngestResponse` / `RejectItem`
  - `Page`
  - `AppView` / `CreateAppRequest` / `IssueKeyRequest` / `ApiKeyIssuedView` / `ApiKeyView`
  - `ErrorCode` enum（引 detail-design §3 字典）
- `paths`：每个 endpoint 含
  - request body / params 完整 schema
  - 200 response
  - 4xx/5xx 响应通过 `ErrorCode` enum 映射

### CI 校验

- 在 `.github/workflows/openapi.yml` 加 `swagger-cli validate docs/api/v1/openapi.yaml`
- 任何 PR 改 controller 签名必须同步该文件（PR 模板 checkbox）

## 验收标准

- [ ] `docs/api/v1/openapi.yaml` 存在且 `swagger-cli validate` 通过
- [ ] 8 endpoint 全部含请求体 / 响应体 / 错误码引用
- [ ] `ResponseResult<T>` 用 `components.schemas` 复用，无重复 inline 定义
- [ ] `securitySchemes` 三种 scope 区分，每个 endpoint 显式声明 `security`
- [ ] README 索引到该文件并标注消费方为 `mcp-platform/mcp-log-viewer`
- [ ] CI workflow 校验通过
