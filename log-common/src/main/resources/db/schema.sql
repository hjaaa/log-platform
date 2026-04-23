-- ============================================================
-- log-platform schema · REQ-2026-001
-- 详见 requirements/REQ-2026-001/artifacts/detailed-design.md §1
--
-- 全局约定：
--   字符集：utf8mb4 / utf8mb4_general_ci
--   引擎：InnoDB
--   时间精度：DATETIME(3)（UTC，应用层按需转时区）
--   主键：BIGINT AUTO_INCREMENT
--   枚举：VARCHAR + 应用层校验（不用 MySQL ENUM，迁移友好）
-- ============================================================

-- ========== app_registrations · 应用注册元信息 ==========
CREATE TABLE IF NOT EXISTS `app_registrations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL COMMENT '业务编码，全局唯一，例：order-service',
  `name` VARCHAR(128) NOT NULL COMMENT '展示名',
  `owner` VARCHAR(64) NOT NULL COMMENT '负责人或团队',
  `environment` VARCHAR(16) NOT NULL COMMENT '枚举：dev/staging/prod',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '枚举：active/disabled',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用注册表';

-- ========== api_keys · 鉴权密钥 ==========
CREATE TABLE IF NOT EXISTS `api_keys` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `app_id` BIGINT UNSIGNED NOT NULL COMMENT '软引用 app_registrations.id（不建 FK，写入热路径无跨表锁）',
  `key_prefix` CHAR(8) NOT NULL COMMENT '明文前 8 位，仅供管理员辨识（脱敏展示）',
  `key_hash` CHAR(64) NOT NULL COMMENT 'SHA-256(明文) 16 进制小写',
  `scope` VARCHAR(8) NOT NULL COMMENT '枚举：write/read',
  `label` VARCHAR(64) DEFAULT NULL COMMENT 'R5：自定义描述，例 order-service-write-prod',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '枚举：active/revoked',
  `last_used_at` DATETIME(3) DEFAULT NULL COMMENT 'AuthFilter 写入；Caffeine 60s 节流',
  `expires_at` DATETIME(3) DEFAULT NULL COMMENT '可空；NULL 视为永不过期',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key_hash` (`key_hash`),
  KEY `idx_api_key_app` (`app_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key 表';

-- ========== logs · 日志主表 ==========
-- 二期改造入口：按 server_ts 月分区（PARTITION BY RANGE COLUMNS(server_ts)）
CREATE TABLE IF NOT EXISTS `logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `app_id` BIGINT UNSIGNED NOT NULL COMMENT '软引用 app_registrations.id',
  `log_kind` VARCHAR(16) NOT NULL COMMENT '枚举：application/access/test',
  `level` VARCHAR(8) NOT NULL COMMENT '枚举：ERROR/WARN/INFO/DEBUG',
  `message` TEXT NOT NULL COMMENT '日志正文；服务端按 max-message-length（字符数）校验，超长由 SDK 端按字符截断',
  `stack_trace` TEXT DEFAULT NULL,
  `context_data` TEXT DEFAULT NULL COMMENT 'JSON 字符串；业务层手动序列化（二期可升级为 JSON 类型支持 path 检索）',
  `trace_id` VARCHAR(64) DEFAULT NULL,
  `span_id` VARCHAR(32) DEFAULT NULL,
  `request_id` VARCHAR(64) DEFAULT NULL,
  `logger_name` VARCHAR(255) DEFAULT NULL,
  `thread_name` VARCHAR(64) DEFAULT NULL,
  `source_host` VARCHAR(64) DEFAULT NULL COMMENT '产生日志的主机名 / Pod 名',
  `client_ts` DATETIME(3) NOT NULL COMMENT '业务侧产生时间（SDK 填）',
  `server_ts` DATETIME(3) NOT NULL COMMENT '服务侧落库时间（log-ingestion 填）',
  PRIMARY KEY (`id`),
  KEY `idx_logs_app_ts` (`app_id`, `server_ts` DESC),
  KEY `idx_logs_trace` (`trace_id`),
  KEY `idx_logs_app_level_ts` (`app_id`, `level`, `server_ts` DESC),
  KEY `idx_logs_kind_ts` (`log_kind`, `server_ts` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志主表';
