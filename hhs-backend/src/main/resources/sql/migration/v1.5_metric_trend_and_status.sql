-- =====================================================
-- HHS 健康指标趋势规范化与历史回溯校准
-- 文件: hhs-backend/src/main/resources/sql/migration/v1.5_metric_trend_and_status.sql
-- 日期: 2026-11-22
-- =====================================================
--
-- 【背景】
--   此前 health_metric 表中 trend 默认字段为 'NORMAL'，
--   导致前端将异常指标错误地渲染为“稳定”，且编辑弹窗中无法匹配
--   UP/DOWN/STABLE 选项而回显英文 'NORMAL'。且历史数据全部默认存为 NORMAL/STABLE，
--   没有根据测量历史时间序列计算真实升降趋势。
--
-- 【本脚本做三件事】
--   1. 修改 health_metric 表结构：将 trend 字段默认值修改为 'STABLE'。
--   2. 修正大小写与非法值：
--      - 将 'up' 统一修正为 'UP'，'down' 修正为 'DOWN'，'stable' 修正为 'STABLE'。
--   3. 历史时间序列趋势智能回溯校准：
--      - 针对每个用户、每个指标，按记录时间顺序（record_date ASC, id ASC），
--        利用窗口函数与前一次数值对比：
--        - 首条记录或数值相同：STABLE
--        - 相比前次数值上升：UP
--        - 相比前次数值下降：DOWN
--
-- 【幂等性】
--   本脚本多次执行结果完全一致，安全幂等。
-- =====================================================

USE hhs;

-- 1. 修改 trend 默认值为 'STABLE'
ALTER TABLE `health_metric` 
    MODIFY COLUMN `trend` VARCHAR(16) DEFAULT 'STABLE' COMMENT 'Trend direction: UP, DOWN, STABLE';

-- 2. 清洗存量数据中的大小写与历史 NORMAL 默认值
UPDATE `health_metric` 
SET `trend` = 'STABLE' 
WHERE `trend` IS NULL 
   OR `trend` = '' 
   OR `trend` = 'NORMAL' 
   OR `trend` = 'normal' 
   OR `trend` = 'stable';

UPDATE `health_metric` 
SET `trend` = 'UP' 
WHERE `trend` = 'up';

UPDATE `health_metric` 
SET `trend` = 'DOWN' 
WHERE `trend` = 'down';

-- 3. 按时间序回溯计算所有指标的真实升降趋势
UPDATE `health_metric` m
JOIN (
    SELECT id,
           CASE 
               WHEN LAG(value) OVER (PARTITION BY user_id, metric_key ORDER BY record_date ASC, id ASC) IS NULL THEN 'STABLE'
               WHEN value > LAG(value) OVER (PARTITION BY user_id, metric_key ORDER BY record_date ASC, id ASC) THEN 'UP'
               WHEN value < LAG(value) OVER (PARTITION BY user_id, metric_key ORDER BY record_date ASC, id ASC) THEN 'DOWN'
               ELSE 'STABLE'
           END as calculated_trend
    FROM `health_metric`
) t ON m.id = t.id
SET m.trend = t.calculated_trend;
