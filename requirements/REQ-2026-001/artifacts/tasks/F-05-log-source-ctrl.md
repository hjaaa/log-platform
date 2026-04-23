---
feature_id: F-05-log-source-ctrl
title: log-source · admin 4 个 endpoint
status: done
type: feature
module: log-source
effort_pd: 1
depends_on: [F-04-log-source-auth]
refs:
  - artifacts/detailed-design.md#22-控制面admin-scope4-个
---

## 背景

应用注册与 API Key 管理控制面。仅 admin token 可调用，scope 在 AuthFilter 已校验。

## 实施清单

### Controller（包 `com.hj.log.source.controller`）

- `AppController`
  - `POST /api/v1/apps` → 注册应用
  - `GET /api/v1/apps` → 列表（query: code/environment/status）
- `ApiKeyController`
  - `POST /api/v1/apps/{id}/keys` → 签发 key
  - `DELETE /api/v1/apps/{id}/keys/{keyId}` → 撤销

### DTO（包 `com.hj.log.source.dto`）

- 入参：`CreateAppRequest` / `IssueKeyRequest`（含 `@NotBlank` / `@Pattern` / `@Size`）
- 出参：`AppView` / `ApiKeyIssuedView`（**不含** `keyHash`）/ `ApiKeyView`

### Service / Mapper

- `AppService.create / list`
- `ApiKeyService.issue / revoke / listByAppId`
- `apiKeyService.issue` 实现：
  - `byte[] random = SecureRandom.getInstanceStrong().generateSeed(32)`
  - `plaintext = "lpk_" + Base32.encode(random).substring(0, 36)`
  - `keyPrefix = plaintext.substring(0, 8)`
  - `keyHash = sha256Hex(plaintext)`
  - 入库 + 返回明文（仅一次）

### Mapper XML

- `log-source/src/main/resources/mapper/source/AppMapper.xml`
- `log-source/src/main/resources/mapper/source/ApiKeyMapper.xml`
- 路径前缀含 `source/` 子目录避免日后跨 module 重名

## 验收标准

- [ ] 4 endpoint 通过 MockMvc 集成测试：含错误路径 `APP_CODE_DUPLICATE` / `APP_NOT_FOUND` / `KEY_NOT_FOUND` / `KEY_INVALID_SCOPE`
- [ ] `POST /apps/{id}/keys` 响应不出现 `keyHash` 字段（JSONPath 断言）
- [ ] key 签发使用 `SecureRandom`，明文长度 ≥ 40 字符（含 `lpk_` 前缀）
- [ ] `DELETE` 走逻辑撤销（`status=revoked`），不物理删除（H2 中查询行仍存在）
- [ ] `AppCode` 字段校验 `[a-z0-9-]+`，违例返回 `BAD_REQUEST`
- [ ] `mvn -pl log-source test` 全绿
