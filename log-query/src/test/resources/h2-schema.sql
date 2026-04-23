-- H2 schema 等价于生产 MySQL DDL（移除 InnoDB / utf8mb4 / DESC 索引方向 / ON UPDATE 等不支持语法）。
-- 仅 log-query 模块的 Mapper 集成测试使用。生产 DDL 见 log-common/src/main/resources/db/schema.sql

CREATE TABLE app_registrations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  owner VARCHAR(64) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (code)
);

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
