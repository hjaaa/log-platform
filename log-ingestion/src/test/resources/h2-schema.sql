-- H2 兼容 schema（与生产 MySQL DDL 等价；移除 InnoDB / utf8mb4 / DESC 索引方向 / ON UPDATE CURRENT_TIMESTAMP 等 H2 不支持语法）
-- 仅用于 log-ingestion 模块的 Mapper 集成测试。完整 DDL 见 log-common/src/main/resources/db/schema.sql

CREATE TABLE logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  app_id BIGINT NOT NULL,
  log_kind VARCHAR(16) NOT NULL,
  level VARCHAR(8) NOT NULL,
  message TEXT NOT NULL,
  stack_trace TEXT,
  context_data TEXT,
  trace_id VARCHAR(64),
  span_id VARCHAR(32),
  request_id VARCHAR(64),
  logger_name VARCHAR(255),
  thread_name VARCHAR(64),
  source_host VARCHAR(64),
  client_ts TIMESTAMP(3) NOT NULL,
  server_ts TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_logs_app_ts ON logs (app_id, server_ts);
CREATE INDEX idx_logs_trace ON logs (trace_id);
CREATE INDEX idx_logs_app_level_ts ON logs (app_id, level, server_ts);
CREATE INDEX idx_logs_kind_ts ON logs (log_kind, server_ts);
