-- =====================================================
-- HHS 风险评估历史留痕与归因快照
-- 文件: hhs-backend/src/main/resources/sql/migration/v1.4_risk_snapshot.sql
-- 日期: 2026-11-17
-- =====================================================
--
-- 【背景】
--   此前 createAssessment 每次都先删除该用户全部记录再插入，导致：
--     1. 风险趋势无法追踪（旧分数直接消失）；
--     2. 归因只能读时按「当前」档案与指标重算，用户补填档案后回看旧记录，
--        明细与分数对不上。
--
-- 【本脚本做两件事】
--   1. 新增 batch_id：同一次评估产生的多条疾病记录共享一个批次号，
--      列表可按批次折叠，也便于计算「本次 vs 上次」的变化。
--   2. 新增 contributors：把当时的归因（含取值、阈值、得分、状态）冻结为 JSON，
--      读取时直接返回，不再重算 —— 历史记录永远是「当时真实依据」。
--
-- 【幂等性】
--   * 两个 ALTER 均以 information_schema 判定列是否已存在，重复执行安全。
--   * 存量行的 batch_id 按 create_time 秒级回填：同一秒插入的三条记录
--     （旧实现一次事务写三条）自然归为同一批次。
--   * 存量行的 contributors 保持 NULL —— 旧记录从未保存过归因，
--     伪造一份「按当前数据重算」的明细正是本次要消除的问题。
--     前端对 NULL 显示「该记录创建时未保存评分明细」。
--   * MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，故用预处理语句实现幂等。
--
-- 【执行顺序】
--   先发代码（新代码同时写旧列与新列），再执行本脚本。反序会让新代码
--   因缺列而启动失败。本脚本只做加法，不修改任何既有列，旧代码亦可运行。
-- =====================================================

USE hhs;

-- ---------- 1. batch_id ----------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_assessment'
      AND COLUMN_NAME = 'batch_id'
);

SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE `risk_assessment`
       ADD COLUMN `batch_id` VARCHAR(32) DEFAULT NULL
         COMMENT ''Assessment batch id; rows produced by one run share it''
         AFTER `profile_id`,
       ADD KEY `idx_batch_id` (`batch_id`)',
    'SELECT ''batch_id already exists, skipped'' AS note'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. contributors ----------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'risk_assessment'
      AND COLUMN_NAME = 'contributors'
);

SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE `risk_assessment`
       ADD COLUMN `contributors` JSON DEFAULT NULL
         COMMENT ''Frozen per-factor scoring breakdown at assessment time''',
    'SELECT ''contributors already exists, skipped'' AS note'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 回填存量行的 batch_id ----------
-- 旧实现一次事务写入同一用户的 3 条记录，create_time 精确到秒且相同，
-- 因此以 user_id + create_time 作为批次标识即可正确还原历史分组。
-- 幂等：已回填的行 batch_id 非空，WHERE 排除后不再更新。
UPDATE `risk_assessment`
SET `batch_id` = CONCAT('legacy-', `user_id`, '-',
                        DATE_FORMAT(`create_time`, '%Y%m%d%H%i%s'))
WHERE `batch_id` IS NULL
  AND `create_time` IS NOT NULL;

-- ---------- 4. 校验 ----------
SELECT
    COUNT(*)                                                AS total_rows,
    SUM(`batch_id` IS NOT NULL)                             AS with_batch_id,
    SUM(`contributors` IS NOT NULL)                         AS with_contributors,
    COUNT(DISTINCT `batch_id`)                              AS distinct_batches
FROM `risk_assessment`;
