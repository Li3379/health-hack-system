-- =====================================================
-- HHS Database Migration Script
-- Version: 1.1
-- Date: 2026-11-04
-- Description: Idempotent data cleansing script to normalize historical
--              metric keys and aliases to canonical camelCase format.
-- Supported Tables:
--   1. health_metric
--   2. realtime_metric
--   3. alert_rule (with unique key conflict protection)
--   4. user_threshold (with user-level unique key conflict protection)
--   5. alert_template
--   6. health_alert
--   7. alert_merge_log
-- =====================================================

-- -----------------------------------------------------
-- 1. Table: health_metric
-- -----------------------------------------------------
UPDATE health_metric SET metric_key = 'heartRate' WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE', 'pulse', 'PULSE');
UPDATE health_metric SET metric_key = 'systolicBP' WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP', 'sbp', 'SBP');
UPDATE health_metric SET metric_key = 'diastolicBP' WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP', 'dbp', 'DBP');
UPDATE health_metric SET metric_key = 'bloodGlucose' WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE', 'glu', 'GLU');
UPDATE health_metric SET metric_key = 'fastingGlucose' WHERE metric_key IN ('fasting_glucose', 'FASTING_GLUCOSE', 'fastingglucose');
UPDATE health_metric SET metric_key = 'postprandialGlucose' WHERE metric_key IN ('postprandial_glucose', 'POSTPRANDIAL_GLUCOSE', 'postprandialglucose');
UPDATE health_metric SET metric_key = 'bodyTemperature' WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE', 'bodytemperature', 'TEMPERATURE', 'TEMP');
UPDATE health_metric SET metric_key = 'weight' WHERE metric_key IN ('WEIGHT');
UPDATE health_metric SET metric_key = 'bmi' WHERE metric_key IN ('BMI');
UPDATE health_metric SET metric_key = 'bloodOxygen' WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2', 'bloodoxygen', 'BLOOD_OXYGEN');
UPDATE health_metric SET metric_key = 'totalCholesterol' WHERE metric_key IN ('total_cholesterol', 'TOTAL_CHOLESTEROL', 'totalcholesterol', 'tc', 'TC');
UPDATE health_metric SET metric_key = 'triglycerides' WHERE metric_key IN ('TRIGLYCERIDES', 'tg', 'TG');
UPDATE health_metric SET metric_key = 'hdlCholesterol' WHERE metric_key IN ('hdl_cholesterol', 'HDL_CHOLESTEROL', 'hdlcholesterol', 'hdl', 'HDL');
UPDATE health_metric SET metric_key = 'ldlCholesterol' WHERE metric_key IN ('ldl_cholesterol', 'LDL_CHOLESTEROL', 'ldlcholesterol', 'ldl', 'LDL');
UPDATE health_metric SET metric_key = 'uricAcid' WHERE metric_key IN ('uric_acid', 'URIC_ACID', 'uricacid', 'ua', 'UA');
UPDATE health_metric SET metric_key = 'creatinine' WHERE metric_key IN ('CREATININE', 'cr', 'CR');
UPDATE health_metric SET metric_key = 'sleepDuration' WHERE metric_key IN ('sleep_duration', 'SLEEP_DURATION', 'sleepduration');
UPDATE health_metric SET metric_key = 'sleepQuality' WHERE metric_key IN ('sleep_quality', 'SLEEP_QUALITY', 'sleepquality');
UPDATE health_metric SET metric_key = 'steps' WHERE metric_key IN ('STEPS', 'step', 'STEP');
UPDATE health_metric SET metric_key = 'exerciseMinutes' WHERE metric_key IN ('exercise_minutes', 'EXERCISE_MINUTES', 'exerciseminutes');
UPDATE health_metric SET metric_key = 'waterIntake' WHERE metric_key IN ('water_intake', 'WATER_INTAKE', 'waterintake');
UPDATE health_metric SET metric_key = 'mood' WHERE metric_key IN ('MOOD');
UPDATE health_metric SET metric_key = 'energy' WHERE metric_key IN ('ENERGY');

-- -----------------------------------------------------
-- 2. Table: realtime_metric
-- -----------------------------------------------------
UPDATE realtime_metric SET metric_key = 'heartRate' WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE', 'pulse', 'PULSE');
UPDATE realtime_metric SET metric_key = 'systolicBP' WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP', 'sbp', 'SBP');
UPDATE realtime_metric SET metric_key = 'diastolicBP' WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP', 'dbp', 'DBP');
UPDATE realtime_metric SET metric_key = 'bloodGlucose' WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE', 'glu', 'GLU');
UPDATE realtime_metric SET metric_key = 'fastingGlucose' WHERE metric_key IN ('fasting_glucose', 'FASTING_GLUCOSE', 'fastingglucose');
UPDATE realtime_metric SET metric_key = 'postprandialGlucose' WHERE metric_key IN ('postprandial_glucose', 'POSTPRANDIAL_GLUCOSE', 'postprandialglucose');
UPDATE realtime_metric SET metric_key = 'bodyTemperature' WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE', 'bodytemperature', 'TEMPERATURE', 'TEMP');
UPDATE realtime_metric SET metric_key = 'weight' WHERE metric_key IN ('WEIGHT');
UPDATE realtime_metric SET metric_key = 'bmi' WHERE metric_key IN ('BMI');
UPDATE realtime_metric SET metric_key = 'bloodOxygen' WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2', 'bloodoxygen', 'BLOOD_OXYGEN');
UPDATE realtime_metric SET metric_key = 'totalCholesterol' WHERE metric_key IN ('total_cholesterol', 'TOTAL_CHOLESTEROL', 'totalcholesterol', 'tc', 'TC');
UPDATE realtime_metric SET metric_key = 'triglycerides' WHERE metric_key IN ('TRIGLYCERIDES', 'tg', 'TG');
UPDATE realtime_metric SET metric_key = 'hdlCholesterol' WHERE metric_key IN ('hdl_cholesterol', 'HDL_CHOLESTEROL', 'hdlcholesterol', 'hdl', 'HDL');
UPDATE realtime_metric SET metric_key = 'ldlCholesterol' WHERE metric_key IN ('ldl_cholesterol', 'LDL_CHOLESTEROL', 'ldlcholesterol', 'ldl', 'LDL');
UPDATE realtime_metric SET metric_key = 'uricAcid' WHERE metric_key IN ('uric_acid', 'URIC_ACID', 'uricacid', 'ua', 'UA');
UPDATE realtime_metric SET metric_key = 'creatinine' WHERE metric_key IN ('CREATININE', 'cr', 'CR');
UPDATE realtime_metric SET metric_key = 'sleepDuration' WHERE metric_key IN ('sleep_duration', 'SLEEP_DURATION', 'sleepduration');
UPDATE realtime_metric SET metric_key = 'sleepQuality' WHERE metric_key IN ('sleep_quality', 'SLEEP_QUALITY', 'sleepquality');
UPDATE realtime_metric SET metric_key = 'steps' WHERE metric_key IN ('STEPS', 'step', 'STEP');
UPDATE realtime_metric SET metric_key = 'exerciseMinutes' WHERE metric_key IN ('exercise_minutes', 'EXERCISE_MINUTES', 'exerciseminutes');
UPDATE realtime_metric SET metric_key = 'waterIntake' WHERE metric_key IN ('water_intake', 'WATER_INTAKE', 'waterintake');
UPDATE realtime_metric SET metric_key = 'mood' WHERE metric_key IN ('MOOD');
UPDATE realtime_metric SET metric_key = 'energy' WHERE metric_key IN ('ENERGY');

-- -----------------------------------------------------
-- 3. Table: alert_rule (uk_metric: metric_key is unique)
-- -----------------------------------------------------
-- Delete redundant alias rules if the canonical rule already exists
DELETE FROM alert_rule WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE', 'pulse') AND EXISTS (SELECT 1 FROM (SELECT * FROM alert_rule) r WHERE r.metric_key = 'heartRate');
UPDATE alert_rule SET metric_key = 'heartRate' WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE', 'pulse');

DELETE FROM alert_rule WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP', 'sbp') AND EXISTS (SELECT 1 FROM (SELECT * FROM alert_rule) r WHERE r.metric_key = 'systolicBP');
UPDATE alert_rule SET metric_key = 'systolicBP' WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP', 'sbp');

DELETE FROM alert_rule WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP', 'dbp') AND EXISTS (SELECT 1 FROM (SELECT * FROM alert_rule) r WHERE r.metric_key = 'diastolicBP');
UPDATE alert_rule SET metric_key = 'diastolicBP' WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP', 'dbp');

DELETE FROM alert_rule WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE', 'glu') AND EXISTS (SELECT 1 FROM (SELECT * FROM alert_rule) r WHERE r.metric_key = 'bloodGlucose');
UPDATE alert_rule SET metric_key = 'bloodGlucose' WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE', 'glu');

DELETE FROM alert_rule WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE') AND EXISTS (SELECT 1 FROM (SELECT * FROM alert_rule) r WHERE r.metric_key = 'bodyTemperature');
UPDATE alert_rule SET metric_key = 'bodyTemperature' WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE');

DELETE FROM alert_rule WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2') AND EXISTS (SELECT 1 FROM (SELECT * FROM alert_rule) r WHERE r.metric_key = 'bloodOxygen');
UPDATE alert_rule SET metric_key = 'bloodOxygen' WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2');

-- -----------------------------------------------------
-- 4. Table: user_threshold (uk_user_metric: user_id + metric_key is unique)
-- -----------------------------------------------------
DELETE FROM user_threshold WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE') AND user_id IN (SELECT ut.user_id FROM (SELECT user_id FROM user_threshold WHERE metric_key = 'heartRate') ut);
UPDATE user_threshold SET metric_key = 'heartRate' WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE');

DELETE FROM user_threshold WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP') AND user_id IN (SELECT ut.user_id FROM (SELECT user_id FROM user_threshold WHERE metric_key = 'systolicBP') ut);
UPDATE user_threshold SET metric_key = 'systolicBP' WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP');

DELETE FROM user_threshold WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP') AND user_id IN (SELECT ut.user_id FROM (SELECT user_id FROM user_threshold WHERE metric_key = 'diastolicBP') ut);
UPDATE user_threshold SET metric_key = 'diastolicBP' WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP');

DELETE FROM user_threshold WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE') AND user_id IN (SELECT ut.user_id FROM (SELECT user_id FROM user_threshold WHERE metric_key = 'bloodGlucose') ut);
UPDATE user_threshold SET metric_key = 'bloodGlucose' WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE');

DELETE FROM user_threshold WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE') AND user_id IN (SELECT ut.user_id FROM (SELECT user_id FROM user_threshold WHERE metric_key = 'bodyTemperature') ut);
UPDATE user_threshold SET metric_key = 'bodyTemperature' WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE');

DELETE FROM user_threshold WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2') AND user_id IN (SELECT ut.user_id FROM (SELECT user_id FROM user_threshold WHERE metric_key = 'bloodOxygen') ut);
UPDATE user_threshold SET metric_key = 'bloodOxygen' WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2');

-- -----------------------------------------------------
-- 5. Table: alert_template
-- -----------------------------------------------------
UPDATE alert_template SET metric_key = 'heartRate' WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE');
UPDATE alert_template SET metric_key = 'systolicBP' WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP');
UPDATE alert_template SET metric_key = 'diastolicBP' WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP');
UPDATE alert_template SET metric_key = 'bloodGlucose' WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE');
UPDATE alert_template SET metric_key = 'bodyTemperature' WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE');
UPDATE alert_template SET metric_key = 'bloodOxygen' WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2');

-- -----------------------------------------------------
-- 6. Table: health_alert
-- -----------------------------------------------------
UPDATE health_alert SET metric_key = 'heartRate' WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE');
UPDATE health_alert SET metric_key = 'systolicBP' WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP');
UPDATE health_alert SET metric_key = 'diastolicBP' WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP');
UPDATE health_alert SET metric_key = 'bloodGlucose' WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE');
UPDATE health_alert SET metric_key = 'bodyTemperature' WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE');
UPDATE health_alert SET metric_key = 'bloodOxygen' WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2');

-- -----------------------------------------------------
-- 7. Table: alert_merge_log
-- -----------------------------------------------------
UPDATE alert_merge_log SET metric_key = 'heartRate' WHERE metric_key IN ('heart_rate', 'heartrate', 'HEART_RATE');
UPDATE alert_merge_log SET metric_key = 'systolicBP' WHERE metric_key IN ('systolic_bp', 'systolicBp', 'SYSTOLIC_BP');
UPDATE alert_merge_log SET metric_key = 'diastolicBP' WHERE metric_key IN ('diastolic_bp', 'diastolicBp', 'DIASTOLIC_BP');
UPDATE alert_merge_log SET metric_key = 'bloodGlucose' WHERE metric_key IN ('blood_glucose', 'glucose', 'GLUCOSE');
UPDATE alert_merge_log SET metric_key = 'bodyTemperature' WHERE metric_key IN ('body_temperature', 'temperature', 'temp', 'BODY_TEMPERATURE');
UPDATE alert_merge_log SET metric_key = 'bloodOxygen' WHERE metric_key IN ('blood_oxygen', 'spo2', 'SPO2');
