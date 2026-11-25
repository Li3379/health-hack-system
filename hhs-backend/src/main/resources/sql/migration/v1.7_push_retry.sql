-- =====================================================
-- HHS 推送失败补偿：push_history 增加重试状态
-- 文件: hhs-backend/src/main/resources/sql/migration/v1.7_push_retry.sql
-- 日期: 2026-11-25
-- =====================================================
--
-- 【背景】
--   真实告警投递失败后没有任何补偿：AlertPushListener 吞掉异常只打日志，
--   push_history 里 status='FAILED' 的行无人扫描重发。凭据/webhook 短暂故障
--   期间产生的告警就此永久丢失，用户只能自己去推送设置页翻历史才会发现。
--
-- 【本脚本做两件事】
--   1. push_history 增加 retry_count / next_retry_at 两列，供补偿任务记录进度；
--   2. 增加 (status, next_retry_at) 索引，让「找出该重试的行」是一次索引扫描。
--
-- 【为什么不回填历史 FAILED 行】
--   存量 FAILED 大多来自凭据本身错误（QQ SMTP 535），重试只会继续失败并可能触发
--   服务商的频率限制。补偿任务只处理部署之后新产生的行：next_retry_at 默认 NULL，
--   而扫描条件是 next_retry_at IS NOT NULL，所以旧行天然被排除。
--
-- 【幂等性】
--   ALTER 在 MySQL 8.0 上不幂等，重复执行会报 duplicate column。这是刻意的：
--   迁移脚本由人执行一次，静默跳过会让「到底跑没跑」变得不可判断。
-- =====================================================

ALTER TABLE `push_history`
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0
        COMMENT 'Delivery retries attempted by the compensation job' AFTER `pushed_at`,
    ADD COLUMN `next_retry_at` DATETIME DEFAULT NULL
        COMMENT 'When the compensation job may retry; NULL = not scheduled' AFTER `retry_count`,
    ADD KEY `idx_retry_due` (`status`, `next_retry_at`);
