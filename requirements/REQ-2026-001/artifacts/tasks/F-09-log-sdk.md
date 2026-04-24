---
feature_id: F-09-log-sdk
title: log-sdk · 异步缓冲 + HTTP POST + 失败降级 + replay
status: done
type: feature
module: log-sdk
effort_pd: 4
risk: high
tags: [concurrency, io, client-jar]
depends_on: [F-02-skeleton-6modules]
created_at: 2026-04-23T11:25:00Z
updated_at: 2026-04-24T00:52:37Z
review_report: artifacts/review-20260424-005237.md
refs:
  - artifacts/detailed-design.md#6-log-sdk-详细设计
---

## 背景

独立客户端 jar，三条铁律：**不阻塞业务线程 / 不抛异常 / 失败永不丢业务路径**（降级到本地文件，下周期 replay）。本 feature 风险等级 high，集中并发与 IO 复杂度。

## 实施清单

### 公共 API（detailed-design §6.1）

```java
package com.hj.log.sdk.client;
public final class LogClient implements AutoCloseable {
    public static LogClient create(LogSdkProperties props);
    public void write(LogEvent event);   // 非阻塞、不抛异常
    public void flushNow();              // 测试辅助
    @Override public void close();        // shutdown 收尾
}
```

### LogEvent

- `record LogEvent(String logKind, String level, String message, String stackTrace, String contextData, String traceId, String spanId, String requestId, String loggerName, String threadName, String sourceHost, Instant clientTs)`

### Buffer + Threads（detailed-design §6.2）

- `BlockingDeque<LogEvent> buffer = new LinkedBlockingDeque<>(1024)`
- `FlushThread`：daemon=true，name="log-sdk-flush"，运行循环：
  - `take` 直到满 100 条 OR 距上次 flush 2s
  - 整批 POST → 处理重试（attempt 0..2，退避 `min(4000, 1000<<attempt)` ms）
  - 仍失败 → 转 fallback writer
- `ReplayThread`：daemon=true，name="log-sdk-replay"，每 30s 扫描 fallback dir
  - 按字典序最早文件 → 解析 NDJSON → 重新走 FlushThread 路径
  - 全成功删除文件；任一失败留待下轮

### Fallback writer（detailed-design §6.4 + §6.7）

- 路径：`${log.sdk.fallback-dir}/log-sdk-yyyyMMdd-HH.ndjson`
- 写之前评估目录大小 > `log.sdk.fallback.max-disk-mb` → 按字典序删最旧
- 单行 NDJSON：`{"event": <LogEvent JSON>, "ts": "<ISO8601>"}`
- 并发：`FileChannel.tryLock()`；replay reader 拿不到锁就跳过

### 字段截断（detailed-design §6.6）

- `LogClient.write` 入口先截：`message > 2048` / `stackTrace > 16384` 字符
- 截断后尾部追加 `…[truncated]`（占 12 字符，已计入目标长度）
- `truncatedCount` 计数器，每 60s WARN 一次

### Shutdown hook（detailed-design §6.2）

- `Runtime.getRuntime().addShutdownHook(new Thread(this::close))`
- `close()`：发 poison pill → 等 `log.sdk.shutdown-wait-ms` (默认 5s) → 仍未空 → 把剩余 buffer 写 fallback 文件

### 日志（SDK 自身）

- 用 SLF4J，宿主应用绑定 logback / log4j2 由其决定
- 业务节点 INFO：start / shutdown
- WARN：buffer 满丢弃计数（每 60s）/ 截断计数 / 4xx 不重试
- ERROR：fallback 写失败 / replay 解析失败

## 验收标准

- [ ] MockWebServer 测试矩阵 7 用例（detailed-design §6.5）全绿
- [ ] buffer 满时 `droppedCount` 递增、不抛异常（断言 `Throwable` 未被捕获后再抛）
- [ ] fallback 文件按小时滚动；replay 成功后删除（H2 测试目录断言）
- [ ] shutdown hook 5s 内收尾，超时则把剩余 buffer 写 fallback（用 1500 条 + 阻塞 server 模拟）
- [ ] API 表面零阻塞：`write` 平均耗时 < 50us（JMH 微基准记录在 SDK README 性能段）
- [ ] 字段截断断言：超长输入 → buffer 内事件长度 ≤ 阈值
- [ ] fallback 磁盘上限触发时按字典序删最旧
- [ ] `mvn -pl log-sdk test` 全绿
