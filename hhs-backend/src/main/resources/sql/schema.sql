-- =====================================================
-- HHS Database Schema
-- Version: 3.0.0
-- Created: 2026-02-27
-- Description: Unified schema for all HHS tables
-- Character Set: utf8mb4
-- Collation: utf8mb4_unicode_ci
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
    `trend` VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'Trend: NORMAL/HIGH/LOW',
    `category` ENUM('HEALTH', 'WELLNESS') DEFAULT 'HEALTH' COMMENT 'Metric category: HEALTH-medical/WELLNESS-lifestyle',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_metric_date` (`user_id`, `metric_key`, `record_date`),
    KEY `idx_user_category_date` (`user_id`, `category`, `record_date`),
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
    `alert_type` VARCHAR(50) NOT NULL COMMENT 'Alert type: CRITICAL-severe/WARNING-warning/INFO-tip/TREND-trend',
    `alert_level` VARCHAR(20) NOT NULL COMMENT 'Alert level: HIGH-high/MEDIUM-medium/LOW-low',
    `title` VARCHAR(200) DEFAULT NULL COMMENT 'Alert title',
    `message` TEXT DEFAULT NULL COMMENT 'Alert message',
    `metric_key` VARCHAR(50) DEFAULT NULL COMMENT 'Associated metric type',
    `current_value` DECIMAL(10,2) DEFAULT NULL COMMENT 'Current value',
    `threshold_value` DECIMAL(10,2) DEFAULT NULL COMMENT 'Threshold value',
    `is_read` BOOLEAN DEFAULT FALSE COMMENT 'Is read',
    `is_acknowledged` BOOLEAN DEFAULT FALSE COMMENT 'Is acknowledged',
    `acknowledged_at` DATETIME DEFAULT NULL COMMENT 'Acknowledgment time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_read` (`is_read`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_user_level` (`user_id`, `alert_level`),
    KEY `idx_alert_type` (`alert_type`),
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
-- 8. AI CONVERSATION (depends on sys_user)
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
-- 9. SCREENING RECORD (depends on sys_user)
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

-- =====================================================
-- END OF SCHEMA
-- =====================================================

-- =====================================================
-- MIGRATION SCRIPTS (for existing databases)
-- =====================================================

-- Migration: Add category column to health_metric table (v1.1)
-- Run this script if the category column does not exist

-- Add category column if not exists
-- MySQL 8.0+ syntax for conditional column addition
SET @dbname = DATABASE();
SET @tablename = 'health_metric';
SET @columnname = 'category';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = @dbname
        AND TABLE_NAME = @tablename
        AND COLUMN_NAME = @columnname
    ) > 0,
    'SELECT 1',
    CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `category` ENUM(''HEALTH'', ''WELLNESS'') DEFAULT ''HEALTH'' COMMENT ''Metric category: HEALTH-medical/WELLNESS-lifestyle'' AFTER `trend`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add index for category-based queries (if not exists)
SET @indexname = 'idx_user_category_date';
SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = @dbname
        AND TABLE_NAME = @tablename
        AND INDEX_NAME = @indexname
    ) > 0,
    'SELECT 1',
    CONCAT('CREATE INDEX `', @indexname, '` ON `', @tablename, '` (`user_id`, `category`, `record_date`)')
));
PREPARE createIndexIfNotExists FROM @preparedStatement;
EXECUTE createIndexIfNotExists;
DEALLOCATE PREPARE createIndexIfNotExists;

-- =====================================================
-- END OF MIGRATION SCRIPTS
-- =====================================================
