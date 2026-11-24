-- =====================================================
-- HHS 清理已下线功能的遗留表
-- 文件: hhs-backend/src/main/resources/sql/migration/v1.6_drop_legacy_tables.sql
-- 日期: 2026-11-24
-- =====================================================
--
-- 【背景】
--   三批功能先后下线，但只删了 Java 代码，没有删表：
--     1. 成就 / 积分 / 连续打卡（Gamification）—— 控制器与 service 已移除
--     2. 穿戴设备同步（华为 / 小米 / 微信 / Apple）—— 2026-11 整块下线
--     3. 告警合并日志、删除日志、报表导出任务 —— 从未有实体或调用方
--   这些表在 `src/main` 中已无任何 Java / XML / Mapper 引用，
--   schema.sql 也不再创建它们，属于纯历史残留。
--
-- 【判定依据（2026-11-24 实测）】
--   * 用 @TableName("<table>") 反查，10 张表对应实体数全为 0
--   * grep 全量 hhs-backend/src/main 与 hhs-frontend-v2/src：
--     除本文件与 schema.sql 的历史变更日志注释外无任何引用
--   * isolation 检查：3 个视图（v_latest_metrics_summary /
--     v_unread_alerts_summary / v_user_health_overview）均未引用它们；
--     2 个存储过程（cleanup_expired_score_cache /
--     cleanup_old_realtime_metrics）只操作各自实名的表
--   * 唯一外键为 user_achievements.achievement_id -> achievements.id，
--     两者同批删除，故先删子表再删父表
--
-- 【数据影响】
--   本脚本会**永久删除数据**。执行前实测行数：
--     achievements 5 / user_achievements 5 / user_points 5 / user_streaks 5 /
--     device_platform_config 4（4 条 enabled=0 的空占位行）；
--     其余 5 张表 0 行。
--   如需保留备查，请先自行导出：
--     mysqldump -u root -p --no-create-info --complete-insert hhs \
--       achievements user_achievements user_points user_streaks \
--       device_platform_config > legacy-$(date +%F).sql
--
-- 【幂等性】
--   全部使用 DROP TABLE IF EXISTS，重复执行安全。
--   外键先于父表删除，避免 #1217 foreign key constraint fails。
-- =====================================================

USE hhs;

-- 1. 成就 / 积分 / 连续打卡（子表在前，父表在后）
DROP TABLE IF EXISTS `user_achievements`;
DROP TABLE IF EXISTS `achievements`;
DROP TABLE IF EXISTS `user_points`;
DROP TABLE IF EXISTS `user_streaks`;

-- 2. 穿戴设备同步（模块已于 2026-11 整块下线）
DROP TABLE IF EXISTS `device_connection`;
DROP TABLE IF EXISTS `device_platform_config`;
DROP TABLE IF EXISTS `sync_history`;

-- 3. 从未有调用方的日志 / 任务表
DROP TABLE IF EXISTS `alert_merge_log`;
DROP TABLE IF EXISTS `delete_log`;
DROP TABLE IF EXISTS `export_jobs`;
