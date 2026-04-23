-- H2 兼容版 schema（与生产 MySQL DDL 等价；移除 InnoDB / utf8mb4 / DESC 索引方向 / ON UPDATE CURRENT_TIMESTAMP 等 H2 不支持语法）
-- 仅用于 log-source 模块的 Mapper 集成测试。完整的 MySQL DDL 见 log-common/src/main/resources/db/schema.sql

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
  CONSTRAINT uk_app_code UNIQUE (code)
);

CREATE TABLE api_keys (
  id BIGINT NOT NULL AUTO_INCREMENT,
  app_id BIGINT NOT NULL,
  key_prefix CHAR(8) NOT NULL,
  key_hash CHAR(64) NOT NULL,
  scope VARCHAR(8) NOT NULL,
  label VARCHAR(64),
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  last_used_at TIMESTAMP(3),
  expires_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_api_key_hash UNIQUE (key_hash)
);

CREATE INDEX idx_api_key_app ON api_keys (app_id, status);
