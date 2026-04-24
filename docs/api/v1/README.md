# log-platform REST API · v1

log-platform 对外 REST 契约的**唯一权威来源**。

| 字段 | 值 |
|---|---|
| 契约文件 | [`openapi.yaml`](./openapi.yaml) |
| OpenAPI 版本 | 3.0.3 |
| 路径前缀 | `/api/v1` |
| 设计依据 | [`requirements/REQ-2026-001/artifacts/detailed-design.md`](../../../requirements/REQ-2026-001/artifacts/detailed-design.md) §2（endpoint）/ §3（错误码） |
| 下游消费方 | [`mcp-platform/packages/mcp-log-viewer`](https://github.com/hjaaa/mcp-platform)（跨仓） |

## 契约维护规则

1. **Controller 签名变更 → 契约必改**：任何 `*Controller.java` 的路径、入参、返回体、Bean Validation 调整，PR 必须同步更新 `openapi.yaml`，并在 PR 模板勾选对应项。
2. **Breaking change**：URL 语义变更、必填字段新增、字段改名或删除，视为 breaking。必须在 `mcp-platform` 仓同步创建 issue/PR，否则 mcp-log-viewer 会静默失配。
3. **兼容变更**：仅新增可选字段、仅新增可选错误码、仅新增 endpoint。
4. **CI 校验**：`.github/workflows/openapi.yml` 在 PR 触及 `docs/api/**` 时跑 `swagger-cli validate` + `redocly lint`，任一失败阻断合入。

## 本地校验

```bash
# 推荐（仍在维护）
npx --yes @redocly/cli@latest lint docs/api/v1/openapi.yaml

# 或按 detailed-design §10.2 约定
npx --yes @apidevtools/swagger-cli@latest validate docs/api/v1/openapi.yaml
```

## Endpoint 速查（9 个）

| 类别 | Method | Path | Security |
|---|---|---|---|
| control plane | POST | `/api/v1/apps` | BearerAdmin |
| control plane | GET | `/api/v1/apps` | BearerAdmin |
| control plane | POST | `/api/v1/apps/{appId}/keys` | BearerAdmin |
| control plane | GET | `/api/v1/apps/{appId}/keys` | BearerAdmin |
| control plane | DELETE | `/api/v1/apps/{appId}/keys/{keyId}` | BearerAdmin |
| ingestion | POST | `/api/v1/logs` | BearerWriteKey |
| query | GET | `/api/v1/logs/search` | BearerReadKey |
| query | GET | `/api/v1/logs/{id}` | BearerReadKey |
| query | GET | `/api/v1/logs/trace/{traceId}` | BearerReadKey |

字段与错误码详情以 [`openapi.yaml`](./openapi.yaml) 为准。
