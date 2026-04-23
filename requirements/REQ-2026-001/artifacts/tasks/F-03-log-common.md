---
feature_id: F-03-log-common
title: log-common · domain / enum / exception / context
status: done
type: feature
module: log-common
effort_pd: 1
depends_on: [F-02-skeleton-6modules]
refs:
  - artifacts/detailed-design.md#1-数据库设计
  - artifacts/detailed-design.md#42-requestcontextcomhjlogcommoncontextrequestcontext
  - artifacts/detailed-design.md#3-错误码字典comhjlogcommonexceptionerrorcode
---

## 背景

log-common 是其他 5 个后端 module 的共享基础。本 feature 落 DDL 文件、DO、enum、异常体系、ResponseResult、RequestContext。

## 实施清单

### DDL（detailed-design §1.2）

- 路径：`log-common/src/main/resources/db/schema.sql`
- 三表完整 DDL（`app_registrations` / `api_keys` / `logs`），含字符集、索引、注释、二期分区入口注释

### Java domain（DO，对应表）

- `com.hj.log.common.domain.AppRegistration`
- `com.hj.log.common.domain.ApiKey`
- `com.hj.log.common.domain.LogEntry`
- `com.hj.log.common.domain.BaseEntity`（含 `id`、`createdAt`，`updatedAt`）

### enum（detailed-design §3 / §4.2）

- `com.hj.log.common.enums.Scope` { ADMIN, WRITE, READ }
- `com.hj.log.common.enums.LogLevel` { ERROR, WARN, INFO, DEBUG }
- `com.hj.log.common.enums.LogKind` { application, access, test }
- `com.hj.log.common.enums.KeyStatus` { active, revoked }
- `com.hj.log.common.enums.Environment` { dev, staging, prod }
- `com.hj.log.common.enums.AppStatus` { active, disabled }

### 异常 + 响应

- `com.hj.log.common.exception.BusinessException`：`code` (String) + `message` + `httpStatus` (int, 默认 400)
- `com.hj.log.common.exception.ErrorCode`：常量类，承载 §3 字典的 code 字符串与默认 message
- `com.hj.log.common.base.ResponseResult<T>`：含 `code` / `message` / `data`；静态工厂 `ok(data)` / `fail(code, msg)`

### RequestContext（detailed-design §4.2）

- `com.hj.log.common.context.RequestContext`
- `set(...)` 包私有，仅 `com.hj.log.common.context` 包内 / `log-source` 通过 `package-info.java` 友元控制（详见 detail-design §4.2）
- 字段：`appId` (Long, nullable for admin)、`scope`、`keyId` (Long, nullable)、`requestId`

## 验收标准

- [ ] `schema.sql` 落 `log-common/src/main/resources/db/`，含 3 表完整 DDL
- [ ] 全部 enum 类存在，命名与 detailed-design §3 / §4.2 一致
- [ ] `RequestContext.set` 包私有；非授权调用编译失败
- [ ] `ResponseResult<T>` 字段与现有约定一致（code/message/data）
- [ ] `BusinessException` 默认 httpStatus=400，可通过构造重载传入
- [ ] log-common 单元测试覆盖 enum 反射、RequestContext.clear 行为
- [ ] `mvn -pl log-common test` 全绿
