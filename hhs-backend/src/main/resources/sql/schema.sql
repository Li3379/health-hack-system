-- =====================================================
-- HHS Database Schema
-- Version: 3.5.0
-- Created: 2026-02-27
-- Updated: 2026-03-19
-- Description: Unified schema for all HHS tables
-- Character Set: utf8mb4
-- Collation: utf8mb4_unicode_ci
--
-- Change Log:
--   v3.5.0 (2026-03-19): Added device_platform_config table for OAuth configuration management
--   v3.4.0 (2026-03-19): Added record_time column to health_metric for precise timestamp conflict resolution
--   v3.3.0 (2026-03-05): Added push_history table for multi-channel push tracking
--   v3.2.0 (2026-03-04): Added alert optimization tables (user_push_config, alert_template, alert_merge_log)
--                        Added fields to health_alert for AI analysis and intelligent deduplication
--   v3.1.0 (2026-03-04): Added delete_log table, health_report table
--   v3.0.0 (2026-02-27): Initial unified schema
-- =====================================================

-- =====================================================
-- TABLES IN DEPENDENCY ORDER
-- =====================================================

-- ============================================================================
-- 1. USER MANAGEMENT (no dependencies)
-- ============================================================================

-- User table
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'User ID',
    `username` VARCHAR(50) NOT NULL COMMENT 'Username',
    `password` VARCHAR(255) NOT NULL COMMENT 'Password (BCrypt encrypted)',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT 'Nickname',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT 'Avatar URL',
    `email` VARCHAR(100) DEFAULT NULL COMMENT 'Email',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT 'Phone number',
    `status` TINYINT DEFAULT 1 COMMENT 'Status: 0-disabled, 1-normal',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User table';

-- Delete log table (operation audit)
CREATE TABLE IF NOT EXISTS `delete_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Log ID',
    `operator_id` BIGINT NOT NULL COMMENT 'Operator user ID',
    `operator_name` VARCHAR(50) DEFAULT NULL COMMENT 'Operator nickname',
    `target_type` VARCHAR(32) NOT NULL COMMENT 'Target type: TIP/COMMENT/REPORT etc',
    `target_id` BIGINT NOT NULL COMMENT 'Target ID',
    `target_title` VARCHAR(255) DEFAULT NULL COMMENT 'Target title or summary',
    `delete_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Delete time',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT 'Operator IP address',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Remark',
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_delete_time` (`delete_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Delete operation log table';

-- ============================================================================
-- 2. HEALTH PROFILE (depends on sys_user)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `health_profile` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Profile ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `gender` VARCHAR(16) DEFAULT NULL COMMENT 'Gender',
    `birth_date` DATE DEFAULT NULL COMMENT 'Birth date',
    `height_cm` DECIMAL(5,2) DEFAULT NULL COMMENT 'Height (cm)',
    `weight_kg` DECIMAL(5,2) DEFAULT NULL COMMENT 'Weight (kg)',
    `bmi` DECIMAL(4,2) DEFAULT NULL COMMENT 'BMI',
    `blood_type` VARCHAR(16) DEFAULT NULL COMMENT 'Blood type',
    `allergy_history` VARCHAR(512) DEFAULT NULL COMMENT 'Allergy history',
    `family_history` VARCHAR(512) DEFAULT NULL COMMENT 'Family history',
    `lifestyle_habits` VARCHAR(512) DEFAULT NULL COMMENT 'Lifestyle habits',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_health_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health profile table';

-- ============================================================================
-- 3. HEALTH METRIC (depends on sys_user)
-- ============================================================================

-- Health metric history table (structured records)
CREATE TABLE IF NOT EXISTS `health_metric` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Metric ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `profile_id` BIGINT DEFAULT NULL COMMENT 'Profile ID',
    `metric_key` VARCHAR(32) NOT NULL COMMENT 'Metric type: BMI/blood pressure/glucose/heart rate etc',
    `value` DECIMAL(10,2) NOT NULL COMMENT 'Value',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT 'Unit',
    `record_date` DATE NOT NULL COMMENT 'Record date',
    `record_time` DATETIME DEFAULT NULL COMMENT 'Precise timestamp for conflict resolution (v3.4.0)',
    `trend` VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'Trend: NORMAL/HIGH/LOW',
    `category` ENUM('HEALTH', 'WELLNESS') DEFAULT 'HEALTH' COMMENT 'Metric category: HEALTH-medical/WELLNESS-lifestyle',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_metric_date` (`user_id`, `metric_key`, `record_date`),
    KEY `idx_user_category_date` (`user_id`, `category`, `record_date`),
    KEY `idx_user_metric_time` (`user_id`, `metric_key`, `record_time`),
    CONSTRAINT `fk_health_metric_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health metric history table';

-- ============================================================================
-- 4. REALTIME METRIC (depends on sys_user)
-- ============================================================================

-- Realtime metric table (high concurrency write optimized)
-- Note: MySQL partitioned tables do not support foreign key constraints, data consistency maintained at application layer
CREATE TABLE IF NOT EXISTS `realtime_metric` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Metric ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `metric_key` VARCHAR(50) NOT NULL COMMENT 'Metric type: heartRate/systolicBP/diastolicBP/glucose/weight/bmi/temperature',
    `value` DECIMAL(10,2) NOT NULL COMMENT 'Metric value',
    `unit` VARCHAR(20) DEFAULT NULL COMMENT 'Unit',
    `source` VARCHAR(50) DEFAULT 'manual' COMMENT 'Data source: manual-device-api',
    `quality_score` DECIMAL(3,2) DEFAULT NULL COMMENT 'Data quality score (0-1)',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation time (millisecond precision)',
    PRIMARY KEY (`id`, `created_at`),
    KEY `idx_user_metric_time` (`user_id`, `metric_key`, `created_at`),
    KEY `idx_time_series` (`created_at`),
    KEY `idx_user_latest` (`user_id`, `metric_key`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Realtime health metric table'
PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202602 VALUES LESS THAN (202603),
    PARTITION p202603 VALUES LESS THAN (202604),
    PARTITION p202604 VALUES LESS THAN (202605),
    PARTITION p202605 VALUES LESS THAN (202606),
    PARTITION p202606 VALUES LESS THAN (202607),
    PARTITION p202607 VALUES LESS THAN (202608),
    PARTITION p202608 VALUES LESS THAN (202609),
    PARTITION p202609 VALUES LESS THAN (202610),
    PARTITION p202610 VALUES LESS THAN (202611),
    PARTITION p202611 VALUES LESS THAN (202612),
    PARTITION p202612 VALUES LESS THAN (202701),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- ============================================================================
-- 5. HEALTH ALERT (depends on sys_user)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `health_alert` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Alert ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `alert_type` VARCHAR(50) NOT NULL COMMENT 'Alert type: CRITICAL-severe/WARNING-warning/INFO-tip/TREND-trend/RECOVERY-recovery',
    `alert_level` VARCHAR(20) NOT NULL COMMENT 'Alert level: HIGH-high/MEDIUM-medium/LOW-low',
    `title` VARCHAR(200) DEFAULT NULL COMMENT 'Alert title',
    `message` TEXT DEFAULT NULL COMMENT 'Alert message',
    `metric_key` VARCHAR(50) DEFAULT NULL COMMENT 'Associated metric type',
    `current_value` DECIMAL(10,2) DEFAULT NULL COMMENT 'Current value',
    `threshold_value` DECIMAL(10,2) DEFAULT NULL COMMENT 'Threshold value',
    `occurrence_count` INT DEFAULT 1 COMMENT 'Occurrence count (for merged alerts)',
    `last_occurrence_at` DATETIME DEFAULT NULL COMMENT 'Last occurrence time',
    `ai_analysis` TEXT DEFAULT NULL COMMENT 'AI analysis result',
    `suggestion` TEXT DEFAULT NULL COMMENT 'Health suggestion',
    `push_channels` VARCHAR(200) DEFAULT NULL COMMENT 'Pushed channels (JSON array)',
    `is_read` BOOLEAN DEFAULT FALSE COMMENT 'Is read',
    `is_acknowledged` BOOLEAN DEFAULT FALSE COMMENT 'Is acknowledged',
    `acknowledged_at` DATETIME DEFAULT NULL COMMENT 'Acknowledgment time',
    `resolved_at` DATETIME DEFAULT NULL COMMENT 'Resolution time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_read` (`is_read`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_user_level` (`user_id`, `alert_level`),
    KEY `idx_alert_type` (`alert_type`),
    KEY `idx_user_metric` (`user_id`, `metric_key`),
    KEY `idx_resolved` (`user_id`, `resolved_at`),
    CONSTRAINT `fk_health_alert_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health alert table';

-- Alert rule table
CREATE TABLE IF NOT EXISTS `alert_rule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Rule ID',
    `metric_key` VARCHAR(50) NOT NULL COMMENT 'Metric type',
    `warning_high` DECIMAL(10,2) DEFAULT NULL COMMENT 'Warning high threshold',
    `critical_high` DECIMAL(10,2) DEFAULT NULL COMMENT 'Critical high threshold',
    `warning_low` DECIMAL(10,2) DEFAULT NULL COMMENT 'Warning low threshold',
    `critical_low` DECIMAL(10,2) DEFAULT NULL COMMENT 'Critical low threshold',
    `enabled` BOOLEAN DEFAULT TRUE COMMENT 'Is enabled',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'Rule description',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY `uk_metric` (`metric_key`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Alert rule table';

-- User personalized threshold table
CREATE TABLE IF NOT EXISTS `user_threshold` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Threshold ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `metric_key` VARCHAR(50) NOT NULL COMMENT 'Metric type',
    `warning_high` DECIMAL(10,2) DEFAULT NULL COMMENT 'Warning high threshold',
    `critical_high` DECIMAL(10,2) DEFAULT NULL COMMENT 'Critical high threshold',
    `warning_low` DECIMAL(10,2) DEFAULT NULL COMMENT 'Warning low threshold',
    `critical_low` DECIMAL(10,2) DEFAULT NULL COMMENT 'Critical low threshold',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY `uk_user_metric` (`user_id`, `metric_key`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_user_threshold_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User personalized threshold table';

-- User push channel configuration table
CREATE TABLE IF NOT EXISTS `user_push_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Config ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `channel_type` VARCHAR(20) NOT NULL COMMENT 'Channel type: EMAIL/WECOM/FEISHU/WEBSOCKET',
    `config_key` VARCHAR(100) DEFAULT NULL COMMENT 'Config key (e.g., webhook, email)',
    `config_value` VARCHAR(500) DEFAULT NULL COMMENT 'Config value (webhook URL, email address, etc.)',
    `enabled` TINYINT DEFAULT 1 COMMENT 'Is enabled: 0-disabled, 1-enabled',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY `uk_user_channel` (`user_id`, `channel_type`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_channel_type` (`channel_type`),
    CONSTRAINT `fk_user_push_config_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User push channel configuration table';

-- Push history table for tracking all push attempts
CREATE TABLE IF NOT EXISTS `push_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'History ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `alert_id` BIGINT DEFAULT NULL COMMENT 'Associated alert ID',
    `channel_type` VARCHAR(20) NOT NULL COMMENT 'Channel type: WEBSOCKET/EMAIL/WECOM/FEISHU',
    `status` VARCHAR(20) NOT NULL COMMENT 'Push status: SUCCESS/FAILED/SKIPPED',
    `message` VARCHAR(500) DEFAULT NULL COMMENT 'Result message or error description',
    `pushed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Push timestamp',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_alert_id` (`alert_id`),
    KEY `idx_channel_type` (`channel_type`),
    KEY `idx_pushed_at` (`pushed_at`),
    KEY `idx_user_channel_time` (`user_id`, `channel_type`, `pushed_at`),
    CONSTRAINT `fk_push_history_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Push history table';

-- Alert message template table
CREATE TABLE IF NOT EXISTS `alert_template` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Template ID',
    `template_key` VARCHAR(100) NOT NULL COMMENT 'Template unique key (e.g., heartRate.high.mild)',
    `metric_key` VARCHAR(50) NOT NULL COMMENT 'Metric type',
    `severity_level` VARCHAR(20) NOT NULL COMMENT 'Severity level: CRITICAL/WARNING/INFO',
    `condition_expr` VARCHAR(200) DEFAULT NULL COMMENT 'Trigger condition expression',
    `title_template` VARCHAR(200) NOT NULL COMMENT 'Title template (supports {value} placeholder)',
    `message_template` TEXT NOT NULL COMMENT 'Message template (supports {value}, {threshold} placeholders)',
    `suggestion_template` TEXT DEFAULT NULL COMMENT 'Suggestion template',
    `priority` INT DEFAULT 0 COMMENT 'Priority (higher value = higher priority)',
    `enabled` TINYINT DEFAULT 1 COMMENT 'Is enabled: 0-disabled, 1-enabled',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY `uk_template_key` (`template_key`),
    KEY `idx_metric_severity` (`metric_key`, `severity_level`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Alert message template table';

-- Alert merge log table (for intelligent deduplication)
CREATE TABLE IF NOT EXISTS `alert_merge_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Log ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `primary_alert_id` BIGINT NOT NULL COMMENT 'Primary alert ID',
    `merged_alert_ids` JSON DEFAULT NULL COMMENT 'Merged alert ID list (JSON array)',
    `merge_count` INT DEFAULT 1 COMMENT 'Merge count',
    `metric_key` VARCHAR(50) NOT NULL COMMENT 'Metric type',
    `first_occurrence_at` DATETIME NOT NULL COMMENT 'First occurrence time',
    `last_occurrence_at` DATETIME NOT NULL COMMENT 'Last occurrence time',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_primary_alert` (`primary_alert_id`),
    KEY `idx_user_metric` (`user_id`, `metric_key`),
    KEY `idx_first_occurrence` (`first_occurrence_at`),
    CONSTRAINT `fk_alert_merge_log_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Alert merge log table';

-- Health score cache table
CREATE TABLE IF NOT EXISTS `health_score_cache` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Cache ID',
    `user_id` BIGINT NOT NULL UNIQUE COMMENT 'User ID',
    `score` INT NOT NULL COMMENT 'Health score (0-100)',
    `level` VARCHAR(20) NOT NULL COMMENT 'Health level: EXCELLENT-excellent/GOOD-good/FAIR-average/POOR-poor',
    `factors` JSON DEFAULT NULL COMMENT 'Score factor details (JSON format)',
    `calculated_at` DATETIME NOT NULL COMMENT 'Calculation time',
    `expires_at` DATETIME NOT NULL COMMENT 'Expiration time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expires_at` (`expires_at`),
    CONSTRAINT `fk_health_score_cache_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health score cache table';

-- ============================================================================
-- 6. EXAMINATION REPORT (depends on sys_user)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `examination_report` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Report ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `report_name` VARCHAR(128) NOT NULL COMMENT 'Report name',
    `report_type` VARCHAR(32) DEFAULT NULL COMMENT 'Report type',
    `institution` VARCHAR(128) DEFAULT NULL COMMENT 'Institution name',
    `report_date` DATE DEFAULT NULL COMMENT 'Report date',
    `file_url` VARCHAR(512) NOT NULL COMMENT 'File URL',
    `ocr_status` VARCHAR(32) DEFAULT 'PENDING' COMMENT 'OCR status: PENDING-pending/PROCESSING-processing/SUCCESS-success/FAILED-failed',
    `abnormal_summary` TEXT DEFAULT NULL COMMENT 'Abnormal metric summary (including error messages)',
    `structured_data` JSON DEFAULT NULL COMMENT 'Structured data (JSON format)',
    `original_filename` VARCHAR(500) DEFAULT NULL COMMENT 'Original filename (user uploaded filename)',
    `stored_filename` VARCHAR(500) DEFAULT NULL COMMENT 'Stored filename (UUID safe filename)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_report_date` (`report_date`),
    KEY `idx_ocr_status` (`ocr_status`),
    CONSTRAINT `fk_examination_report_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Examination report table';

-- Lab result table
CREATE TABLE IF NOT EXISTS `lab_result` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Result ID',
    `report_id` BIGINT NOT NULL COMMENT 'Report ID',
    `name` VARCHAR(64) NOT NULL COMMENT 'Metric name',
    `category` VARCHAR(64) DEFAULT NULL COMMENT 'Category',
    `value` VARCHAR(64) NOT NULL COMMENT 'Test value',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT 'Unit',
    `reference_range` VARCHAR(128) DEFAULT NULL COMMENT 'Reference range',
    `is_abnormal` TINYINT DEFAULT 0 COMMENT 'Is abnormal: 0-no, 1-yes',
    `trend` VARCHAR(16) DEFAULT NULL COMMENT 'Trend',
    `sort_order` INT DEFAULT 0 COMMENT 'Sort order',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_report_id` (`report_id`),
    KEY `idx_sort_order` (`sort_order`),
    CONSTRAINT `fk_lab_result_report` FOREIGN KEY (`report_id`) REFERENCES `examination_report` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Lab test result table';

-- OCR recognition log table
CREATE TABLE IF NOT EXISTS `ocr_recognize_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Log ID',
    `report_id` BIGINT NOT NULL COMMENT 'Associated report ID',
    `status` VARCHAR(32) NOT NULL COMMENT 'Status: PROCESSING-processing/SUCCESS-success/FAILED-failed',
    `raw_text` MEDIUMTEXT DEFAULT NULL COMMENT 'OCR recognized text (MEDIUMTEXT supports long text)',
    `confidence` DECIMAL(5,2) DEFAULT NULL COMMENT 'Recognition confidence',
    `duration_ms` INT DEFAULT NULL COMMENT 'Processing duration (milliseconds)',
    `error_message` VARCHAR(512) DEFAULT NULL COMMENT 'Error message',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `original_filename` VARCHAR(500) DEFAULT NULL COMMENT 'Original filename',
    `stored_filename` VARCHAR(500) DEFAULT NULL COMMENT 'Stored filename',
    KEY `idx_report_id` (`report_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    CONSTRAINT `fk_ocr_log_report` FOREIGN KEY (`report_id`) REFERENCES `examination_report` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OCR recognition log table';

-- ============================================================================
-- 7. RISK ASSESSMENT (depends on sys_user)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `risk_assessment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Assessment ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `profile_id` BIGINT DEFAULT NULL COMMENT 'Profile ID',
    `disease_name` VARCHAR(64) NOT NULL COMMENT 'Disease name',
    `risk_level` VARCHAR(16) NOT NULL COMMENT 'Risk level: LOW-low/MEDIUM-medium/HIGH-high',
    `risk_score` INT DEFAULT NULL COMMENT 'Risk score (0-100)',
    `suggestion` TEXT DEFAULT NULL COMMENT 'Suggestion measures',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_risk_level` (`risk_level`),
    CONSTRAINT `fk_risk_assessment_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Risk assessment record table';

-- ============================================================================
-- 8. HEALTH REPORT (depends on sys_user)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `health_report` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Report ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `report_id` VARCHAR(32) NOT NULL COMMENT 'Report unique identifier',
    `overall_score` INT NOT NULL COMMENT 'Overall health score (0-100)',
    `score_level` VARCHAR(20) NOT NULL COMMENT 'Score level: EXCELLENT/GOOD/FAIR/POOR',
    `dimensions` JSON NOT NULL COMMENT 'Dimension analysis data (JSON)',
    `risk_alerts` JSON DEFAULT NULL COMMENT 'Risk alert data (JSON)',
    `suggestions` JSON NOT NULL COMMENT 'Improvement suggestions (JSON)',
    `summary` TEXT NOT NULL COMMENT 'Health summary',
    `user_info` JSON NOT NULL COMMENT 'User info snapshot (JSON)',
    `generated_at` DATETIME NOT NULL COMMENT 'Report generation time',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    UNIQUE KEY `uk_report_id` (`report_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_generated_at` (`generated_at`),
    KEY `idx_user_generated` (`user_id`, `generated_at` DESC),
    CONSTRAINT `fk_health_report_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Health report table';

-- ============================================================================
-- 9. AI CONVERSATION (depends on sys_user)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Conversation ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT 'Session ID',
    `question` TEXT NOT NULL COMMENT 'User question',
    `answer` TEXT DEFAULT NULL COMMENT 'AI answer',
    `tokens_used` INT DEFAULT 0 COMMENT 'Tokens used',
    `model_version` VARCHAR(50) DEFAULT NULL COMMENT 'Model version',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_ai_conversation_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI conversation record table';

-- ============================================================================
-- 10. SCREENING RECORD (depends on sys_user)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `screening_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Screening ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `screening_type` VARCHAR(50) NOT NULL COMMENT 'Screening type',
    `screening_date` DATE NOT NULL COMMENT 'Screening date',
    `result` VARCHAR(50) DEFAULT NULL COMMENT 'Screening result',
    `notes` TEXT DEFAULT NULL COMMENT 'Notes',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_screening_type` (`screening_type`),
    KEY `idx_screening_date` (`screening_date`),
    CONSTRAINT `fk_screening_record_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Screening record table';

-- =====================================================
-- VIEWS
-- =====================================================

-- User health overview view
CREATE OR REPLACE VIEW `v_user_health_overview` AS
SELECT
    u.id AS user_id,
    u.username,
    u.nickname,
    u.avatar,
    hp.bmi,
    (SELECT COUNT(*) FROM health_metric WHERE user_id = u.id) AS metric_count,
    (SELECT COUNT(*) FROM examination_report WHERE user_id = u.id) AS report_count,
    (SELECT COUNT(*) FROM risk_assessment WHERE user_id = u.id AND created_at >= DATE_SUB(NOW(), INTERVAL 6 MONTH)) AS recent_risk_count
FROM sys_user u
LEFT JOIN health_profile hp ON u.id = hp.user_id;

-- Latest metric summary view
CREATE OR REPLACE VIEW `v_latest_metrics_summary` AS
SELECT
    rm.user_id,
    rm.metric_key,
    rm.id AS latest_id,
    rm.value AS latest_value,
    rm.unit,
    rm.created_at AS latest_time
FROM realtime_metric rm
INNER JOIN (
    SELECT user_id, metric_key, MAX(id) AS max_id
    FROM realtime_metric
    GROUP BY user_id, metric_key
) latest ON rm.id = latest.max_id;

-- Unread alerts summary view
CREATE OR REPLACE VIEW `v_unread_alerts_summary` AS
SELECT
    user_id,
    COUNT(*) AS unread_count,
    SUM(CASE WHEN alert_type = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_count,
    SUM(CASE WHEN alert_type = 'WARNING' THEN 1 ELSE 0 END) AS warning_count
FROM health_alert
WHERE is_read = FALSE
GROUP BY user_id;

-- =====================================================
-- STORED PROCEDURES
-- =====================================================

DELIMITER //

-- Cleanup old realtime metrics
CREATE PROCEDURE `cleanup_old_realtime_metrics`(
    IN retention_months INT
)
BEGIN
    DECLARE cutoff_date DATETIME;
    SET cutoff_date = DATE_SUB(NOW(), INTERVAL retention_months MONTH);

    DELETE FROM realtime_metric WHERE created_at < cutoff_date;

    SELECT ROW_COUNT() AS deleted_rows;
END //

-- Cleanup expired health score cache
CREATE PROCEDURE `cleanup_expired_score_cache`()
BEGIN
    DELETE FROM health_score_cache WHERE expires_at < NOW();

    SELECT ROW_COUNT() AS deleted_rows;
END //

DELIMITER ;

-- =====================================================
-- INITIAL DATA
-- =====================================================

-- Insert default alert rules
INSERT INTO `alert_rule` (`metric_key`, `warning_high`, `critical_high`, `warning_low`, `critical_low`, `description`) VALUES
('heartRate', 100, 120, 50, 40, 'Heart rate (bpm): normal range 60-100'),
('systolicBP', 140, 160, 90, 80, 'Systolic blood pressure (mmHg): normal range 90-140'),
('diastolicBP', 90, 100, 60, 50, 'Diastolic blood pressure (mmHg): normal range 60-90'),
('glucose', 7.0, 11.1, 3.9, 3.0, 'Fasting blood glucose (mmol/L): normal range 3.9-6.1'),
('bmi', 28, 35, 18.5, 16, 'BMI: normal range 18.5-24'),
('temperature', 37.5, 38.5, 36.0, 35.5, 'Body temperature (C): normal range 36.0-37.3'),
('weight', NULL, NULL, NULL, NULL, 'Weight (kg): dynamically assessed based on height')
ON DUPLICATE KEY UPDATE
    `warning_high` = VALUES(`warning_high`),
    `critical_high` = VALUES(`critical_high`),
    `warning_low` = VALUES(`warning_low`),
    `critical_low` = VALUES(`critical_low`),
    `description` = VALUES(`description`);

-- Insert default alert templates
INSERT INTO `alert_template` (`template_key`, `metric_key`, `severity_level`, `condition_expr`, `title_template`, `message_template`, `suggestion_template`, `priority`) VALUES
-- Heart rate templates
('heartRate.high.critical', 'heartRate', 'CRITICAL', 'value > 120', '心率严重偏高', '您的心率达到 {value} bpm，严重超过正常范围(60-100 bpm)。', '请立即停止活动并休息。如伴有胸闷、气短、头晕等症状，请立即就医或拨打急救电话。', 100),
('heartRate.high.warning', 'heartRate', 'WARNING', 'value > 100 && value <= 120', '心率偏高', '您的心率为 {value} bpm，超过正常范围(60-100 bpm)。', '建议停止当前活动，休息15-20分钟后复测。如持续偏高，建议就医检查。', 80),
('heartRate.low.critical', 'heartRate', 'CRITICAL', 'value < 40', '心率严重偏低', '您的心率低至 {value} bpm，严重低于正常范围。', '请立即就医检查，可能存在心脏传导问题。', 100),
('heartRate.low.warning', 'heartRate', 'WARNING', 'value < 50 && value >= 40', '心率偏低', '您的心率为 {value} bpm，低于正常范围(60-100 bpm)。', '如为运动员可能属正常。如伴有头晕、乏力，建议就医检查。', 80),

-- Blood pressure (systolic) templates
('systolicBP.high.critical', 'systolicBP', 'CRITICAL', 'value > 160', '收缩压严重偏高', '您的收缩压达到 {value} mmHg，严重超过正常范围(90-140 mmHg)。', '请立即停止活动，保持安静，如持续不降请立即就医。', 100),
('systolicBP.high.warning', 'systolicBP', 'WARNING', 'value > 140 && value <= 160', '收缩压偏高', '您的收缩压为 {value} mmHg，超过正常范围(90-140 mmHg)。', '建议减少盐分摄入，保持规律作息，避免情绪激动。如持续偏高，建议就医。', 80),
('systolicBP.low.warning', 'systolicBP', 'WARNING', 'value < 90', '收缩压偏低', '您的收缩压为 {value} mmHg，低于正常范围(90-140 mmHg)。', '如伴有头晕、乏力，建议适量增加盐分摄入，避免突然起身。', 60),

-- Blood pressure (diastolic) templates
('diastolicBP.high.critical', 'diastolicBP', 'CRITICAL', 'value > 100', '舒张压严重偏高', '您的舒张压达到 {value} mmHg，严重超过正常范围(60-90 mmHg)。', '请立即停止活动，保持安静，如持续不降请立即就医。', 100),
('diastolicBP.high.warning', 'diastolicBP', 'WARNING', 'value > 90 && value <= 100', '舒张压偏高', '您的舒张压为 {value} mmHg，超过正常范围(60-90 mmHg)。', '建议减少盐分摄入，保持规律作息。如持续偏高，建议就医。', 80),

-- Blood glucose templates
('glucose.high.critical', 'glucose', 'CRITICAL', 'value > 11.1', '血糖严重偏高', '您的空腹血糖达到 {value} mmol/L，严重超过正常范围(3.9-6.1 mmol/L)。', '请尽快就医检查，可能需要调整治疗方案。如伴有口渴、多尿等症状，请立即就医。', 100),
('glucose.high.warning', 'glucose', 'WARNING', 'value > 7.0 && value <= 11.1', '血糖偏高', '您的空腹血糖为 {value} mmol/L，超过正常范围(3.9-6.1 mmol/L)。', '建议控制饮食，减少糖分摄入，适当运动。建议复查血糖。', 80),
('glucose.low.critical', 'glucose', 'CRITICAL', 'value < 3.0', '血糖严重偏低', '您的血糖低至 {value} mmol/L，存在低血糖风险。', '请立即补充糖分(如糖果、果汁)，如症状持续请就医。', 100),
('glucose.low.warning', 'glucose', 'WARNING', 'value < 3.9 && value >= 3.0', '血糖偏低', '您的空腹血糖为 {value} mmol/L，低于正常范围(3.9-6.1 mmol/L)。', '建议随身携带糖果，定时进餐，避免空腹运动。', 80),

-- BMI templates
('bmi.high.critical', 'bmi', 'CRITICAL', 'value > 35', 'BMI严重超标', '您的BMI达到 {value}，严重超过正常范围(18.5-24)。', '建议尽快咨询营养师或医生，制定科学的减重计划。', 90),
('bmi.high.warning', 'bmi', 'WARNING', 'value > 28 && value <= 35', 'BMI超标', '您的BMI为 {value}，超过正常范围(18.5-24)。', '建议控制饮食，增加运动量，逐步减轻体重。', 70),
('bmi.low.warning', 'bmi', 'WARNING', 'value < 18.5', 'BMI偏低', '您的BMI为 {value}，低于正常范围(18.5-24)。', '建议增加营养摄入，适量增加蛋白质和健康脂肪。', 60),

-- Temperature templates
('temperature.high.critical', 'temperature', 'CRITICAL', 'value > 38.5', '体温过高', '您的体温达到 {value}°C，存在高热风险。', '请立即采取物理降温措施，如症状持续或加重请就医。', 100),
('temperature.high.warning', 'temperature', 'WARNING', 'value > 37.5 && value <= 38.5', '体温偏高', '您的体温为 {value}°C，略高于正常范围(36.0-37.3°C)。', '建议多喝水，注意休息，监测体温变化。', 70),
('temperature.low.warning', 'temperature', 'WARNING', 'value < 36.0', '体温偏低', '您的体温为 {value}°C，低于正常范围(36.0-37.3°C)。', '建议注意保暖，如持续偏低请就医检查。', 60)
ON DUPLICATE KEY UPDATE
    `message_template` = VALUES(`message_template`),
    `suggestion_template` = VALUES(`suggestion_template`),
    `priority` = VALUES(`priority`);

-- =====================================================
-- END OF SCHEMA
-- =====================================================

-- =====================================================
-- 11. SMART DATA INPUT TABLES (v3.4.0)
-- =====================================================

-- Device connection table (for wearable device sync)
CREATE TABLE IF NOT EXISTS `device_connection` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Connection ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `platform` VARCHAR(20) NOT NULL COMMENT 'Platform: huawei, xiaomi, wechat, apple',
    `platform_user_id` VARCHAR(100) DEFAULT NULL COMMENT 'Platform user identifier',
    `access_token` VARCHAR(500) DEFAULT NULL COMMENT 'Access token (encrypted)',
    `refresh_token` VARCHAR(500) DEFAULT NULL COMMENT 'Refresh token (encrypted)',
    `token_expire_at` DATETIME DEFAULT NULL COMMENT 'Token expiration time',
    `last_sync_at` DATETIME DEFAULT NULL COMMENT 'Last sync time',
    `sync_enabled` TINYINT DEFAULT 1 COMMENT 'Auto sync enabled: 0-disabled, 1-enabled',
    `status` VARCHAR(20) DEFAULT 'connected' COMMENT 'Status: connected, expired, disconnected',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    UNIQUE KEY `uk_user_platform` (`user_id`, `platform`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_platform` (`platform`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_device_connection_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Device connection configuration table';

-- Sync history table
CREATE TABLE IF NOT EXISTS `sync_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'History ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `platform` VARCHAR(20) NOT NULL COMMENT 'Platform: huawei, xiaomi, wechat, apple',
    `sync_type` VARCHAR(20) NOT NULL COMMENT 'Sync type: manual, scheduled',
    `metrics_count` INT DEFAULT 0 COMMENT 'Number of metrics synced',
    `status` VARCHAR(20) NOT NULL COMMENT 'Status: success, partial, failed',
    `error_message` TEXT DEFAULT NULL COMMENT 'Error message',
    `start_time` DATETIME NOT NULL COMMENT 'Sync start time',
    `end_time` DATETIME DEFAULT NULL COMMENT 'Sync end time',
    `duration_ms` INT DEFAULT NULL COMMENT 'Duration in milliseconds',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_platform` (`platform`, `create_time`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_sync_history_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Device sync history table';

-- AI parse history table
CREATE TABLE IF NOT EXISTS `ai_parse_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Parse ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `input_text` TEXT NOT NULL COMMENT 'User input text',
    `input_type` VARCHAR(20) DEFAULT 'text' COMMENT 'Input type: text, voice',
    `parse_result` JSON DEFAULT NULL COMMENT 'Parse result JSON',
    `confirmed` TINYINT DEFAULT 0 COMMENT 'Confirmed and saved: 0-no, 1-yes',
    `metric_ids` JSON DEFAULT NULL COMMENT 'Saved metric ID list (JSON array)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_confirmed` (`confirmed`),
    CONSTRAINT `fk_ai_parse_history_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI parse history table';

-- OCR health image recognition record table (Phase 3)
CREATE TABLE IF NOT EXISTS `ocr_health_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Record ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `ocr_type` VARCHAR(20) NOT NULL COMMENT 'OCR type: report, medicine, nutrition',
    `raw_text` MEDIUMTEXT DEFAULT NULL COMMENT 'Raw OCR recognized text',
    `parse_result` JSON DEFAULT NULL COMMENT 'Parsed metrics JSON',
    `confirmed` TINYINT DEFAULT 0 COMMENT 'Confirmed and saved: 0-no, 1-yes',
    `metric_ids` JSON DEFAULT NULL COMMENT 'Saved metric ID list (JSON array)',
    `original_filename` VARCHAR(500) DEFAULT NULL COMMENT 'Original uploaded filename',
    `stored_filename` VARCHAR(500) DEFAULT NULL COMMENT 'Stored filename (UUID)',
    `status` VARCHAR(20) DEFAULT 'success' COMMENT 'Recognition status: success, failed',
    `error_message` TEXT DEFAULT NULL COMMENT 'Error message',
    `duration_ms` INT DEFAULT NULL COMMENT 'Recognition duration (milliseconds)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_ocr_type` (`ocr_type`),
    KEY `idx_confirmed` (`confirmed`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_ocr_health_record_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OCR health image recognition record table';

-- =====================================================
-- MIGRATION SCRIPTS (For existing databases)
-- =====================================================

-- Migration v3.4.0: Add record_time column to health_metric table
-- This column enables precise timestamp-based conflict resolution for device sync
-- Run this script if the column does not exist

-- Check if column exists before adding (MySQL 8.0+)
-- For MySQL 5.7, use: SHOW COLUMNS FROM health_metric LIKE 'record_time';

-- Add record_time column if not exists
SET @dbname = DATABASE();
SET @tablename = 'health_metric';
SET @columnname = 'record_time';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname
     AND TABLE_NAME = @tablename
     AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` DATETIME DEFAULT NULL COMMENT ''Precise timestamp for conflict resolution (v3.4.0)'' AFTER `record_date`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add index for record_time if not exists
SET @indexname = 'idx_user_metric_time';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = @dbname
     AND TABLE_NAME = @tablename
     AND INDEX_NAME = @indexname) > 0,
    'SELECT 1',
    CONCAT('CREATE INDEX `', @indexname, '` ON `', @tablename, '` (`user_id`, `metric_key`, `record_time`)')
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;

-- =====================================================
-- 12. DEVICE PLATFORM CONFIG TABLE (v3.5.0)
-- =====================================================

-- Device platform configuration table
-- Stores OAuth credentials for each wearable device platform
CREATE TABLE IF NOT EXISTS `device_platform_config` (
    `platform` VARCHAR(20) PRIMARY KEY COMMENT 'Platform: huawei, xiaomi, wechat, apple',
    `client_id` VARCHAR(500) DEFAULT NULL COMMENT 'OAuth client ID (encrypted)',
    `client_secret` VARCHAR(500) DEFAULT NULL COMMENT 'OAuth client secret (encrypted)',
    `auth_url` VARCHAR(500) DEFAULT NULL COMMENT 'OAuth authorization URL',
    `token_url` VARCHAR(500) DEFAULT NULL COMMENT 'Token exchange URL',
    `redirect_uri` VARCHAR(500) DEFAULT NULL COMMENT 'OAuth callback URL',
    `scopes` JSON DEFAULT NULL COMMENT 'Permission scopes (JSON array)',
    `configured` TINYINT DEFAULT 0 COMMENT 'Is configured: 0-no, 1-yes',
    `last_test_time` DATETIME DEFAULT NULL COMMENT 'Last configuration test time',
    `test_result` VARCHAR(20) DEFAULT NULL COMMENT 'Test result: success, failed, pending',
    `test_error_message` TEXT DEFAULT NULL COMMENT 'Test error message',
    `enabled` TINYINT DEFAULT 0 COMMENT 'Is enabled: 0-disabled, 1-enabled',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    KEY `idx_configured` (`configured`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Device platform OAuth configuration table';

-- =====================================================
-- END OF SCHEMA
-- =====================================================
