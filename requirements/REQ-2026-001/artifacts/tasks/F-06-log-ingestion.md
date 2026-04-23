---
feature_id: F-06-log-ingestion
title: log-ingestion · 批量写入 + 保留期清理任务
status: done
type: feature
module: log-ingestion
effort_pd: 2
depends_on: [F-04-log-source-auth]
refs:
  - artifacts/detailed-design.md#23-写入面write-scope1-个
  - artifacts/detailed-design.md#14-保留期清理logretentiontask
---

## 背景

唯一写入端点 `POST /api/v1/logs`，承载 SDK 的全部流量；附带保留期清理任务（每小时分批 DELETE）。

## 实施清单

### Controller / Service

- `com.hj.log.ingestion.controller.LogIngestionController.POST /api/v1/logs`
- `com.hj.log.ingestion.service.LogIngestionService`
  - 入参：`IngestRequest { events: List<LogEventDto> }`
  - 校验：见 detail-design §2.3.1（字符长度、枚举、batch size）；不合法的单条入 `rejected[]`，不抛 4xx
- 整批拒绝（`events.size > max-batch-size` 或 events 全空）→ 抛 `BusinessException("INGEST_BATCH_TOO_LARGE", 400)`

### DTO

- `com.hj.log.ingestion.dto.LogEventDto`：与 §2.3.1 请求体字段一致
- `com.hj.log.ingestion.dto.IngestResponse { accepted: int, rejected: List<RejectItem> }`
- `RejectItem { index: int, reason: String }`

### Mapper

- `com.hj.log.ingestion.mapper.LogEntryMapper.batchInsert(List<LogEntry>)`
- XML 单 SQL 多值 INSERT（`<foreach>` 拼接）
- 路径：`log-ingestion/src/main/resources/mapper/ingestion/LogEntryMapper.xml`

### LogRetentionTask

- 包：`com.hj.log.ingestion.task.LogRetentionTask`
- `@Scheduled(cron = "${log.platform.retention.cron:0 17 * * * ?}")`
- 实现循环：
  ```
  while True:
      affected = mapper.deleteOldest(retentionDays, 500)
      if affected < 500: break
      Thread.sleep(10)
  log.info("[retention] deleted={} days={}", total, days)
  ```
- DELETE SQL：`DELETE FROM logs WHERE server_ts < ? ORDER BY id LIMIT 500`

### 配置项

- `log.platform.ingestion.max-batch-size` / `max-message-length` / `max-stack-trace-length`
- `log.platform.retention.days` / `batch-size` / `cron`

## 验收标准

- [ ] `POST /api/v1/logs` 校验全集（batch size / message blank / level / kind / message too long）通过 MockMvc 测试
- [ ] 部分失败时 HTTP 200 + `data.rejected[]` 准确（含 index 与 reason）
- [ ] `LogRetentionTask` 单测覆盖：分批退出条件、SLEEP 调用次数 = 删除批次 - 1
- [ ] Mapper 多值 INSERT 走 H2 集成测试，单批 200 条耗时 < 200ms（性能记录在测试日志）
- [ ] INFO 日志含 `appId, accepted, rejectedCount, costMs`（grep 断言）
- [ ] `mvn -pl log-ingestion test` 全绿
