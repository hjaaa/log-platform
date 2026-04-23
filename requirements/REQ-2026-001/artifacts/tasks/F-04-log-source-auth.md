---
feature_id: F-04-log-source-auth
title: log-source · AuthFilter + Caffeine 节流 + RequestIdFilter
status: done
type: feature
module: log-source
effort_pd: 2
depends_on: [F-03-log-common]
refs:
  - artifacts/detailed-design.md#41-authfilter-详细流程comhjlogsourcefilterauthfilter
  - artifacts/detailed-design.md#43-caffeine-节流last_used_at
  - artifacts/detailed-design.md#52-requestidfilter
---

## 背景

鉴权与 RequestContext 写入的核心实现，所有业务接口都依赖此 feature 上线。Caffeine 节流降低 `last_used_at` UPDATE 频率到 ≤ 10 次/分钟。

## 实施清单

### RequestIdFilter（detailed-design §5.2）

- 包：`com.hj.log.source.filter.RequestIdFilter`
- 顺序：`Ordered.HIGHEST_PRECEDENCE`
- 行为：
  - 取 `X-Request-Id`，无则 `UUID.randomUUID().toString()`
  - 写 MDC `requestId` + 响应 header
  - finally `MDC.remove("requestId")`

### AuthFilter（detailed-design §4.1）

- 包：`com.hj.log.source.filter.AuthFilter`
- 顺序：`Ordered.HIGHEST_PRECEDENCE + 50`
- 豁免路径：`/actuator/**`、`/api/v1/health`
- 流程：按 §4.1 步骤 1-5
- admin token 比较：`MessageDigest.isEqual(byte[], byte[])` 防时序攻击
- API key hash：`SHA-256` 16 进制小写，与 `key_hash` 列存储格式一致
- endpoint scope 区分：通过路径前缀简单分发：
  - `/api/v1/apps*` → admin
  - `/api/v1/logs` (POST) → write
  - `/api/v1/logs/*` (GET) → read

### Caffeine 节流（detailed-design §4.3）

- Bean：`Cache<String, Boolean> authLastUsedCache`，`expireAfterWrite=60s`、`maximumSize=200`
- 异步执行器：`@Async("authExecutor")`，`ThreadPoolTaskExecutor` size=2 / queue=200
- Mapper 方法：`apiKeyMapper.touchLastUsed(Long id, Instant now)` → `UPDATE api_keys SET last_used_at=? WHERE id=?`

### 配置项

- `log.platform.auth.last-used.cache-ttl-seconds` 默认 60
- `log.platform.auth.last-used.cache-max-size` 默认 200
- `log.platform.admin.token` 必填（来自 env `LOG_PLATFORM_ADMIN_TOKEN`）

## 验收标准

- [ ] AuthFilter 通过单元测试覆盖：缺 token / 错 token / 错 scope / key 过期 / key revoked
- [ ] RequestIdFilter 在 AuthFilter 之前注册（FilterRegistrationBean order）
- [ ] Caffeine TTL 60s / 容量 200 配置项可调
- [ ] 异步 UPDATE 走独立线程池 size=2，业务请求线程不阻塞（用 `Thread.currentThread().getName()` 断言）
- [ ] AuthFilter 失败日志只输出 `key_prefix` + `scope`，无 hash / 明文（grep 测试）
- [ ] admin token 比较走 `MessageDigest.isEqual`
- [ ] `mvn -pl log-source test` 全绿
