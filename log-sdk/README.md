# log-sdk

log-platform 的独立 Java 客户端 jar。业务应用 `logger.xxx()` → SDK 异步缓冲 → 批量 HTTP POST 到 `POST /api/v1/logs`；失败自动降级到本地 NDJSON 文件，下周期 replay。

> 架构基线：REQ-2026-001；详见 `requirements/REQ-2026-001/artifacts/detailed-design.md#6-log-sdk-详细设计`。

## 三条铁律

1. **不阻塞业务线程** — `LogClient.write(event)` 走单次 `BlockingDeque.offer`，队列满即丢并计数，永不阻塞
2. **不抛异常** — 入口层 `try/catch(Throwable)` 兜底，任何内部异常都被吞到日志
3. **失败不丢业务路径** — HTTP 5xx / IO 失败指数退避重试；耗尽后转本地 NDJSON 文件；ReplayThread 每 30s 扫并重新 POST；磁盘上限守护（默认 512MB）按字典序删最旧

## 快速上手

```java
LogSdkProperties props = LogSdkProperties.builder()
    .endpoint("http://log-platform.internal/api/v1/logs")
    .apiKey(System.getenv("LOG_SDK_API_KEY"))   // write scope
    .appCode("order-service")
    .build();

LogClient client = LogClient.create(props);

client.write(new LogEvent(
    "application",        // logKind: application/access/test
    "ERROR",              // level
    "payment failed",
    "java.lang.RuntimeException: ...\n  at ...",
    "{\"orderId\":42}",   // contextData（JSON 字符串）
    "trace-abc",          // traceId
    null, null,           // spanId / requestId 可空
    "com.example.Payment",
    Thread.currentThread().getName(),
    "host-1",
    Instant.now()));

// 可选：宿主关闭时显式 close；否则 shutdown hook 自动接管
client.close();
```

## 内部线程模型

| 线程 | 名字 | 职责 | 周期 |
|---|---|---|---|
| FlushThread | `log-sdk-flush` | 消费 buffer → 组批 → HTTP POST → 5xx/IO 退避重试 → 耗尽 fallback | 满 100 条 OR 2s |
| ReplayThread | `log-sdk-replay` | 扫 fallback 目录字典序最早文件 → 解析 NDJSON → 重新走 FlushThread 路径 → 全 OK 删文件 | 30s |

两条线程都是 daemon=true，JVM 退出即止。Shutdown hook 触发 `close()`：停 worker → 最多等 5s → 仍有剩余即把 buffer 写 fallback 文件。

## 配置项（摘，完整见 detailed-design §7.2）

| key | 默认 | 说明 |
|---|---|---|
| `endpoint` | — | 必填；log-platform POST URL |
| `apiKey` | — | 必填；write scope 明文 |
| `appCode` | — | 必填；诊断用 |
| `flushBatchSize` | 100 | flush 触发条数 |
| `flushIntervalMs` | 2000 | flush 触发周期 |
| `bufferCapacity` | 1024 | buffer 容量；满则丢 |
| `httpConnectTimeoutMs` | 3000 | connect 超时 |
| `httpRequestTimeoutMs` | 3000 | request 超时 |
| `httpRetryMaxAttempts` | 3 | 重试次数（不含首次）；首次+3 重试=4 次尝试 |
| `fallbackDir` | `${user.home}/.log-sdk/fallback/` | 降级文件目录 |
| `fallbackMaxDiskMb` | 512 | fallback 目录磁盘上限 |
| `replayIntervalMs` | 30000 | replay 扫描周期 |
| `shutdownWaitMs` | 5000 | shutdown 等待时间 |
| `truncateMessageLength` | 2048 | message 截断阈值（字符数） |
| `truncateStackTraceLength` | 16384 | stackTrace 截断阈值（字符数） |

## 字段截断

入口 `LogClient.write` 先截：`message > 2048` / `stackTrace > 16384` 字符，尾部追加 `…[truncated]`（12 字符，已计入阈值）。截断计数每 60s WARN 一次，与服务端阈值（detailed-design §2.3.1）对齐避免被拒收。

## Fallback 文件格式

每行一条 NDJSON：

```json
{"event": {...LogEvent...}, "ts": "2026-04-24T00:15:02Z"}
```

按小时滚动：`log-sdk-yyyyMMdd-HH.ndjson`，UTC 时区。并发读写通过 `FileChannel.tryLock()` 协调——ReplayThread 拿不到锁即跳过本轮。

## 性能

`LogClient.write` 在本机 M 系列 Mac（JDK 21）上 10000 次循环平均单次 < 10μs，包含字段截断 + `offer` 入队；集成测试断言放宽至 `< 100μs` 以容忍 CI 噪声。热点路径无锁（`LinkedBlockingDeque.offer` 是 CAS 实现）。

## 测试

```bash
source .env && mvn -pl log-sdk test
```

覆盖 detailed-design §6.5 测试矩阵 7 用例（正常 flush / 5xx 重试耗尽 / replay 删文件 / 队列满丢弃 / shutdown 收尾 / 4xx 不重试 / 字段截断）+ 磁盘上限 + write 耗时 + 底层组件 unit test，共 28 用例。

## 依赖

| groupId | artifactId | 用途 |
|---|---|---|
| org.slf4j | slf4j-api | SDK 日志（由宿主绑定实现） |
| com.fasterxml.jackson.core | jackson-databind | JSON 序列化 |
| com.fasterxml.jackson.datatype | jackson-datatype-jsr310 | `Instant` → ISO8601 |

无 Spring / 无后端 module 依赖——`log-sdk` 可独立打 jar 部署到任意 JDK 21 应用。
