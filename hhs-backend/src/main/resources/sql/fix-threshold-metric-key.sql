-- =====================================================
-- HHS 阈值指标键手工修复脚本（Manual One-Off Repair Script）
-- 文件: hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql
-- 日期: 2026-11-08
-- 关联: Phase 12-threshold-wizard FIX-04（决策 D-13 / D-14 / D-15）
-- 覆盖表: user_threshold, alert_rule, alert_template, health_alert
-- =====================================================
--
-- 【用途】
--   将四张表中历史遗留的非标准 metric_key 就地归一化为标准 camelCase key，
--   并在归一化之前合并同族重复行。归一范围是 MetricKeyNormalizer 权威别名
--   映射中以下三个族别的【全部】别名（修复 v1.1 旧迁移脚本只覆盖子集的缺口）：
--     bloodGlucose    <- glucose, GLUCOSE, glu, GLU,
--                        blood_glucose, BLOOD_GLUCOSE, bloodglucose
--     bodyTemperature <- temperature, TEMPERATURE, temp, TEMP,
--                        body_temperature, BODY_TEMPERATURE, bodytemperature
--     bloodOxygen     <- spo2, SPO2, blood_oxygen, BLOOD_OXYGEN, bloodoxygen
--   其余指标 key 在这四张表中本就是标准写法（见 RESEARCH §Runtime State
--   Inventory），且 Phase 12 plan 02 的写入规范化（FIX-02）会拦截新的别名写入。
--
-- 【幂等性保证】
--   可重复执行：
--   * 所有 UPDATE 都带 WHERE metric_key IN (<上方别名全集>) —— 第二次执行时
--     无匹配行，0 行受影响；
--   * 所有 DELETE JOIN 只有在"同族存在更新的一行"时才产生 loser —— 归一化后
--     每族至多一行，无任何匹配，0 行被删；
--   * 第二次执行打印的 AFTER 计数与第一次执行后完全一致（幂等验收依据）。
--
-- 【执行顺序（强制，不可反向）】
--   1) 先部署含 MetricKeyNormalizer 别名 IN 查询 + 写入规范化的后端代码
--      （Phase 12 plan 02，FIX-02/FIX-03）；
--   2) 再手工执行本脚本。
--   旧代码按字面 .eq 精确匹配 key：若先跑脚本后发代码，用户阈值与告警规则
--   对旧代码"查不到"，告警彻底失效（RESEARCH §Pitfall 4）。别名 IN 查询让
--   新代码在脚本执行前后都能命中，唯一安全顺序是 代码发布 -> 脚本执行。
--
-- 【回滚边界】
--   本归一化为单向操作：执行后旧 key 不复存在，不存在自动反向映射；被合并
--   删除的重复行其原始 id 无法恢复。执行前务必记录下方 BEFORE 计数，并先在
--   UAT 环境完成幂等性验证（12-VALIDATION §Manual-Only Verifications）后
--   再对生产库执行。
--
-- 【执行方式（D-13）】
--   由运维手工对目标库执行，例如：
--     mysql -h <host> -P <port> -u <user> -p <database> < fix-threshold-metric-key.sql
--   不引入任何迁移框架，不在应用启动时自动执行。
--   整个数据变更包在单个显式事务内执行：任何一条语句失败则整体回滚，不会
--   留下半归一化状态（威胁项 T-12-08）。
--   全新初始化的数据库（schema.sql 种子已是标准 key）无需执行本脚本。
-- =====================================================

-- ============ Step 0：执行前计数（人工记录留存，T-12-10） ============
-- 非标准计数 = 各表中 metric_key 仍落在上述别名全集内的行数；
-- 执行成功后（见文末 AFTER 块）这些计数必须全部为 0，ut_total 只减不增。
SELECT 'BEFORE' AS phase,
       (SELECT COUNT(*) FROM user_threshold) AS ut_total,
       (SELECT COUNT(*) FROM user_threshold
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS ut_noncanonical,
       (SELECT COUNT(*) FROM alert_rule
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS ar_noncanonical,
       (SELECT COUNT(*) FROM alert_template
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS at_noncanonical,
       (SELECT COUNT(*) FROM health_alert
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS ha_noncanonical;

-- ============ 显式事务开始：以下全部数据变更原子生效 ============
START TRANSACTION;

-- ============ Step 1a：user_threshold —— 先合并同族重复行（D-14 保留最新） ============
-- uk_user_metric (user_id, metric_key) 是"别名盲"的字面唯一索引：若同一用户同时
-- 存在 'glucose' 与 'bloodGlucose'（或任意两个同族别名）两行，先改名会在语句中途
-- 撞唯一键 Duplicate entry 整体回滚（T-12-09）。因此必须先删除同族 loser，
-- 保证进入改名步骤时每族至多一行，改名永不中止。
--
-- 合并语义（D-14 keep-latest）：按 (user_id, 标准族) 分组，只保留 updated_at 最新
-- 的一行（updated_at 相同则保留 id 更大的一行）——用户最近一次设置意图总是获胜：
-- * 更新的别名行淘汰更旧的别名行（alias-alias 同族合并，如 'glucose' + 'GLUCOSE'）；
-- * 更新的别名行也淘汰更旧的标准行（newer alias beats older canonical）；
-- * JOIN 以 CASE 标准映射在两侧同时生效：只有映射到同族的两个不同字面 key 才会
--   配对，非同族（其他指标、其他用户）永不误删。
-- 派生表 (SELECT ...) 物化绕行 MySQL 8「同语句内 DELETE 目标表不得再被子查询
-- SELECT」的限制（MySQL 8.0 Reference Manual §15.2.2）。
DELETE ut
FROM user_threshold ut
JOIN (
    SELECT ut2.id
    FROM user_threshold ut2
    JOIN user_threshold cand
      ON cand.user_id = ut2.user_id
     AND CASE cand.metric_key
             WHEN 'glucose'          THEN 'bloodGlucose'
             WHEN 'GLUCOSE'          THEN 'bloodGlucose'
             WHEN 'glu'              THEN 'bloodGlucose'
             WHEN 'GLU'              THEN 'bloodGlucose'
             WHEN 'blood_glucose'    THEN 'bloodGlucose'
             WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
             WHEN 'bloodglucose'     THEN 'bloodGlucose'
             WHEN 'temperature'      THEN 'bodyTemperature'
             WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
             WHEN 'temp'             THEN 'bodyTemperature'
             WHEN 'TEMP'             THEN 'bodyTemperature'
             WHEN 'body_temperature' THEN 'bodyTemperature'
             WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
             WHEN 'bodytemperature'  THEN 'bodyTemperature'
             WHEN 'spo2'             THEN 'bloodOxygen'
             WHEN 'SPO2'             THEN 'bloodOxygen'
             WHEN 'blood_oxygen'     THEN 'bloodOxygen'
             WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
             WHEN 'bloodoxygen'      THEN 'bloodOxygen'
             ELSE cand.metric_key END
       = CASE ut2.metric_key
             WHEN 'glucose'          THEN 'bloodGlucose'
             WHEN 'GLUCOSE'          THEN 'bloodGlucose'
             WHEN 'glu'              THEN 'bloodGlucose'
             WHEN 'GLU'              THEN 'bloodGlucose'
             WHEN 'blood_glucose'    THEN 'bloodGlucose'
             WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
             WHEN 'bloodglucose'     THEN 'bloodGlucose'
             WHEN 'temperature'      THEN 'bodyTemperature'
             WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
             WHEN 'temp'             THEN 'bodyTemperature'
             WHEN 'TEMP'             THEN 'bodyTemperature'
             WHEN 'body_temperature' THEN 'bodyTemperature'
             WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
             WHEN 'bodytemperature'  THEN 'bodyTemperature'
             WHEN 'spo2'             THEN 'bloodOxygen'
             WHEN 'SPO2'             THEN 'bloodOxygen'
             WHEN 'blood_oxygen'     THEN 'bloodOxygen'
             WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
             WHEN 'bloodoxygen'      THEN 'bloodOxygen'
             ELSE ut2.metric_key END
     AND (   cand.updated_at >  ut2.updated_at
          OR (cand.updated_at = ut2.updated_at AND cand.id > ut2.id))
) AS loser ON loser.id = ut.id;

-- ============ Step 1b：user_threshold —— 剩余非标准行就地改名 ============
-- 此时每个 (user_id, 标准族) 至多一行，改名不会撞 uk_user_metric。
UPDATE user_threshold
SET metric_key = CASE metric_key
        WHEN 'glucose'          THEN 'bloodGlucose'
        WHEN 'GLUCOSE'          THEN 'bloodGlucose'
        WHEN 'glu'              THEN 'bloodGlucose'
        WHEN 'GLU'              THEN 'bloodGlucose'
        WHEN 'blood_glucose'    THEN 'bloodGlucose'
        WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
        WHEN 'bloodglucose'     THEN 'bloodGlucose'
        WHEN 'temperature'      THEN 'bodyTemperature'
        WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
        WHEN 'temp'             THEN 'bodyTemperature'
        WHEN 'TEMP'             THEN 'bodyTemperature'
        WHEN 'body_temperature' THEN 'bodyTemperature'
        WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
        WHEN 'bodytemperature'  THEN 'bodyTemperature'
        WHEN 'spo2'             THEN 'bloodOxygen'
        WHEN 'SPO2'             THEN 'bloodOxygen'
        WHEN 'blood_oxygen'     THEN 'bloodOxygen'
        WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
        WHEN 'bloodoxygen'      THEN 'bloodOxygen'
        ELSE metric_key
END
WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                     'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                     'temperature','TEMPERATURE','temp','TEMP',
                     'body_temperature','BODY_TEMPERATURE','bodytemperature',
                     'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen');

-- ============ Step 2a：alert_rule —— 同样先合并同族重复行（D-15） ============
-- uk_metric (metric_key) 是全局字面唯一索引：若 'glucose' 种子行与 'bloodGlucose'
-- 种子行并存（存量库上手工重放新版 schema 种子可造成），先改名会撞 uk_metric。
-- 合并语义与 Step 1a 一致（D-14 keep-latest，仅无 user_id 维度）。
DELETE ar
FROM alert_rule ar
JOIN (
    SELECT ar2.id
    FROM alert_rule ar2
    JOIN alert_rule cand
      ON CASE cand.metric_key
             WHEN 'glucose'          THEN 'bloodGlucose'
             WHEN 'GLUCOSE'          THEN 'bloodGlucose'
             WHEN 'glu'              THEN 'bloodGlucose'
             WHEN 'GLU'              THEN 'bloodGlucose'
             WHEN 'blood_glucose'    THEN 'bloodGlucose'
             WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
             WHEN 'bloodglucose'     THEN 'bloodGlucose'
             WHEN 'temperature'      THEN 'bodyTemperature'
             WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
             WHEN 'temp'             THEN 'bodyTemperature'
             WHEN 'TEMP'             THEN 'bodyTemperature'
             WHEN 'body_temperature' THEN 'bodyTemperature'
             WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
             WHEN 'bodytemperature'  THEN 'bodyTemperature'
             WHEN 'spo2'             THEN 'bloodOxygen'
             WHEN 'SPO2'             THEN 'bloodOxygen'
             WHEN 'blood_oxygen'     THEN 'bloodOxygen'
             WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
             WHEN 'bloodoxygen'      THEN 'bloodOxygen'
             ELSE cand.metric_key END
       = CASE ar2.metric_key
             WHEN 'glucose'          THEN 'bloodGlucose'
             WHEN 'GLUCOSE'          THEN 'bloodGlucose'
             WHEN 'glu'              THEN 'bloodGlucose'
             WHEN 'GLU'              THEN 'bloodGlucose'
             WHEN 'blood_glucose'    THEN 'bloodGlucose'
             WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
             WHEN 'bloodglucose'     THEN 'bloodGlucose'
             WHEN 'temperature'      THEN 'bodyTemperature'
             WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
             WHEN 'temp'             THEN 'bodyTemperature'
             WHEN 'TEMP'             THEN 'bodyTemperature'
             WHEN 'body_temperature' THEN 'bodyTemperature'
             WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
             WHEN 'bodytemperature'  THEN 'bodyTemperature'
             WHEN 'spo2'             THEN 'bloodOxygen'
             WHEN 'SPO2'             THEN 'bloodOxygen'
             WHEN 'blood_oxygen'     THEN 'bloodOxygen'
             WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
             WHEN 'bloodoxygen'      THEN 'bloodOxygen'
             ELSE ar2.metric_key END
     AND (   cand.updated_at >  ar2.updated_at
          OR (cand.updated_at = ar2.updated_at AND cand.id > ar2.id))
) AS loser ON loser.id = ar.id;

-- ============ Step 2b：alert_rule —— 剩余非标准 key 改名 ============
UPDATE alert_rule
SET metric_key = CASE metric_key
        WHEN 'glucose'          THEN 'bloodGlucose'
        WHEN 'GLUCOSE'          THEN 'bloodGlucose'
        WHEN 'glu'              THEN 'bloodGlucose'
        WHEN 'GLU'              THEN 'bloodGlucose'
        WHEN 'blood_glucose'    THEN 'bloodGlucose'
        WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
        WHEN 'bloodglucose'     THEN 'bloodGlucose'
        WHEN 'temperature'      THEN 'bodyTemperature'
        WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
        WHEN 'temp'             THEN 'bodyTemperature'
        WHEN 'TEMP'             THEN 'bodyTemperature'
        WHEN 'body_temperature' THEN 'bodyTemperature'
        WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
        WHEN 'bodytemperature'  THEN 'bodyTemperature'
        WHEN 'spo2'             THEN 'bloodOxygen'
        WHEN 'SPO2'             THEN 'bloodOxygen'
        WHEN 'blood_oxygen'     THEN 'bloodOxygen'
        WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
        WHEN 'bloodoxygen'      THEN 'bloodOxygen'
        ELSE metric_key
END
WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                     'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                     'temperature','TEMPERATURE','temp','TEMP',
                     'body_temperature','BODY_TEMPERATURE','bodytemperature',
                     'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen');

-- ============ Step 3：alert_template / health_alert —— metric_key 无唯一索引，直接改名 ============
-- alert_template.template_key 内嵌的指标前缀仅供展示，代码从不解析它
-- （只按 metric_key + severity_level 查询，见 RESEARCH §Runtime State Inventory），
-- 故本脚本不触碰 template_key。
UPDATE alert_template
SET metric_key = CASE metric_key
        WHEN 'glucose'          THEN 'bloodGlucose'
        WHEN 'GLUCOSE'          THEN 'bloodGlucose'
        WHEN 'glu'              THEN 'bloodGlucose'
        WHEN 'GLU'              THEN 'bloodGlucose'
        WHEN 'blood_glucose'    THEN 'bloodGlucose'
        WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
        WHEN 'bloodglucose'     THEN 'bloodGlucose'
        WHEN 'temperature'      THEN 'bodyTemperature'
        WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
        WHEN 'temp'             THEN 'bodyTemperature'
        WHEN 'TEMP'             THEN 'bodyTemperature'
        WHEN 'body_temperature' THEN 'bodyTemperature'
        WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
        WHEN 'bodytemperature'  THEN 'bodyTemperature'
        WHEN 'spo2'             THEN 'bloodOxygen'
        WHEN 'SPO2'             THEN 'bloodOxygen'
        WHEN 'blood_oxygen'     THEN 'bloodOxygen'
        WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
        WHEN 'bloodoxygen'      THEN 'bloodOxygen'
        ELSE metric_key
END
WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                     'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                     'temperature','TEMPERATURE','temp','TEMP',
                     'body_temperature','BODY_TEMPERATURE','bodytemperature',
                     'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen');

UPDATE health_alert
SET metric_key = CASE metric_key
        WHEN 'glucose'          THEN 'bloodGlucose'
        WHEN 'GLUCOSE'          THEN 'bloodGlucose'
        WHEN 'glu'              THEN 'bloodGlucose'
        WHEN 'GLU'              THEN 'bloodGlucose'
        WHEN 'blood_glucose'    THEN 'bloodGlucose'
        WHEN 'BLOOD_GLUCOSE'    THEN 'bloodGlucose'
        WHEN 'bloodglucose'     THEN 'bloodGlucose'
        WHEN 'temperature'      THEN 'bodyTemperature'
        WHEN 'TEMPERATURE'      THEN 'bodyTemperature'
        WHEN 'temp'             THEN 'bodyTemperature'
        WHEN 'TEMP'             THEN 'bodyTemperature'
        WHEN 'body_temperature' THEN 'bodyTemperature'
        WHEN 'BODY_TEMPERATURE' THEN 'bodyTemperature'
        WHEN 'bodytemperature'  THEN 'bodyTemperature'
        WHEN 'spo2'             THEN 'bloodOxygen'
        WHEN 'SPO2'             THEN 'bloodOxygen'
        WHEN 'blood_oxygen'     THEN 'bloodOxygen'
        WHEN 'BLOOD_OXYGEN'     THEN 'bloodOxygen'
        WHEN 'bloodoxygen'      THEN 'bloodOxygen'
        ELSE metric_key
END
WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                     'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                     'temperature','TEMPERATURE','temp','TEMP',
                     'body_temperature','BODY_TEMPERATURE','bodytemperature',
                     'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen');

-- ============ 事务结束：以上全部变更原子生效 ============
COMMIT;

-- ============ Step 4：执行后计数（幂等验收，T-12-10） ============
-- 所有 *_noncanonical 必须为 0；ut_total 应等于 BEFORE 的 ut_total 减去被合并
-- 删除的行数。再次执行本脚本时，本 AFTER 计数应与本次完全一致（零行变更）。
SELECT 'AFTER' AS phase,
       (SELECT COUNT(*) FROM user_threshold) AS ut_total,
       (SELECT COUNT(*) FROM user_threshold
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS ut_noncanonical,
       (SELECT COUNT(*) FROM alert_rule
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS ar_noncanonical,
       (SELECT COUNT(*) FROM alert_template
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS at_noncanonical,
       (SELECT COUNT(*) FROM health_alert
         WHERE metric_key IN ('glucose','GLUCOSE','glu','GLU',
                              'blood_glucose','BLOOD_GLUCOSE','bloodglucose',
                              'temperature','TEMPERATURE','temp','TEMP',
                              'body_temperature','BODY_TEMPERATURE','bodytemperature',
                              'spo2','SPO2','blood_oxygen','BLOOD_OXYGEN','bloodoxygen')
       ) AS ha_noncanonical;
