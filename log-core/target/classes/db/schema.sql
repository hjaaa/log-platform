DROP TABLE IF EXISTS area_log_entry;
DROP TABLE IF EXISTS area_alert_rule;
DROP TABLE IF EXISTS area_notification_config;
DROP TABLE IF EXISTS area_log_source;

CREATE TABLE area_log_entry (
    id VARCHAR(32) PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL,
    log_level VARCHAR(16) NOT NULL,
    timestamp DATETIME NOT NULL,
    message TEXT,
    logger_name VARCHAR(256),
    thread_name VARCHAR(128),
    stack_trace TEXT,
    context_data JSON,
    source_ip VARCHAR(64),
    trace_id VARCHAR(64)
);

CREATE TABLE area_alert_rule (
    id VARCHAR(32) PRIMARY KEY,
    rule_name VARCHAR(128) NOT NULL,
    service_name VARCHAR(64),
    log_level VARCHAR(16),
    regex_pattern VARCHAR(512),
    threshold_count INT,
    time_window INT,
    cool_down_period INT,
    enabled TINYINT,
    notification_channels JSON
);

CREATE TABLE area_notification_config (
    id VARCHAR(32) PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    config_data JSON,
    enabled TINYINT
);

CREATE TABLE area_log_source (
    id VARCHAR(32) PRIMARY KEY,
    source_name VARCHAR(128) NOT NULL,
    service_name VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    auth_token VARCHAR(128),
    status VARCHAR(16) NOT NULL
);
