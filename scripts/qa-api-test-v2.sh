#!/usr/bin/env bash
# HHS 全功能白盒 API 测试 v2（修正参数构造）
BASE=http://localhost:8082
TOKEN=$(cat /tmp/hhs_token.txt)
PASS=0; FAIL=0; RESULTS=/tmp/hhs-qa-results2.txt
UID_=$(curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/user/profile | python -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
: > "$RESULTS"

t() {
  local name=$1 method=$2 path=$3 expect=$4 body=$5
  local resp code
  if [ -n "$body" ]; then
    resp=$(curl -s -m 25 -X "$method" "$BASE$path" -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" -d "$body" 2>&1)
  else
    resp=$(curl -s -m 25 -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN" 2>&1)
  fi
  code=$(printf '%s' "$resp" | python -c "import sys,json
try:
    d=json.load(sys.stdin); print(d.get('code','NOCODE'))
except Exception: print('PARSE_ERR')" 2>/dev/null)
  if [ "$code" = "$expect" ]; then
    PASS=$((PASS+1)); printf 'PASS | %-52s | %s\n' "$name" "$code" >> "$RESULTS"
  else
    FAIL=$((FAIL+1)); printf 'FAIL | %-52s | got=%s want=%s | %s\n' "$name" "$code" "$expect" "$(printf '%s' "$resp" | head -c 200)" >> "$RESULTS"
  fi
}

echo "userId=$UID_"

# 健康档案
t "prevention health-profile GET"      GET  /api/prevention/health-profile 200
t "prevention profile GET"             GET  /api/prevention/profile 200
t "prevention profile completeness"    GET  /api/prevention/profile/completeness 200
t "prevention risk-assessment"         GET  /api/prevention/risk-assessment 200
t "health/report 报告(有档案后)"        GET  /api/health/report 200

# 修正参数
t "thresholds by-key"                  GET  "/api/thresholds/by-key?userId=$UID_&metricKey=heartRateQA" 200
t "realtime history"                   GET  "/api/realtime/metrics/history?metricKey=heartRate&hours=24" 200
t "wellness trend"                     GET  "/api/wellness/trend/steps?startDate=2026-11-01&endDate=2026-11-16" 200
t "goals 创建(ASCII)"                  POST /api/goals 200 '{"title":"QA goal 2","description":"t","metricKey":"steps","targetValue":9000,"unit":"steps","startDate":"2026-11-01","endDate":"2026-12-31"}'
t "reminders 创建(ASCII)"              POST /api/reminders 200 '{"title":"QA reminder 2","description":"d","reminderType":"custom","cronExpression":"0 0 10 * * ?"}'
t "wellness 记录(ASCII)"               POST /api/wellness 200 '{"metricKey":"steps","value":9000,"recordDate":"2026-11-16","unit":"steps"}'
t "ai classify 正确字段"               POST /api/ai/classify 200 '{"title":"headache","content":"I have a headache today"}'
t "metrics 体温(ASCII 单位)"           POST /api/metrics 200 '{"metricKey":"bodyTemperature","value":36.6,"recordDate":"2026-11-16","unit":"C"}'

# 真实缺陷探测
t "reminders 空标题(应 400)"           POST /api/reminders 400 '{"description":"no title","reminderType":"custom","cronExpression":"0 0 9 * * ?"}'
t "users 不存在(应 404)"               GET  /api/users/999999 404
t "ocr 不存在(应 404)"                 GET  /api/ocr/history/999999 404
t "export 不存在(应 404)"              GET  /api/exports/999999 404
t "device 未知平台(应 400/404)"        GET  /api/device/platforms/badplatform/availability 400

# 其余全量复测（正常路径）
t "metrics 列表"                       GET  /api/metrics 200
t "metrics metadata"                   GET  /api/metrics/metadata 200
t "health score"                       GET  /api/health/score 200
t "health score breakdown"             GET  /api/health/score/breakdown 200
t "health score history"               GET  /api/health/score/history 200
t "health report history"              GET  /api/health/report/history 200
t "alerts 列表"                        GET  /api/alerts 200
t "alerts unread-count"                GET  /api/alerts/unread-count 200
t "alerts recent"                      GET  /api/alerts/recent 200
t "alerts statistics"                  GET  /api/alerts/statistics 200
t "alerts trend"                       GET  /api/alerts/trend/systolicBloodPressure 200
t "alerts early-warning"               GET  /api/alerts/trend/systolicBloodPressure/early-warning 200
t "goals 列表"                         GET  /api/goals 200
t "mood history"                       GET  /api/mood/history 200
t "mood insights"                      GET  /api/mood/insights 200
t "reminders 列表"                     GET  /api/reminders 200
t "thresholds 列表"                    GET  /api/thresholds 200
t "wellness 列表"                      GET  /api/wellness 200
t "wellness summary"                   GET  /api/wellness/summary 200
t "wellness latest"                    GET  /api/wellness/latest 200
t "realtime 列表"                      GET  /api/realtime/metrics 200
t "realtime latest"                    GET  /api/realtime/metrics/latest 200
t "realtime trend"                     GET  "/api/realtime/metrics/trend?metricKey=heartRate" 200
t "prevention metrics"                 GET  /api/prevention/metrics 200
t "screening reports"                  GET  /api/screening/reports 200
t "stats today"                        GET  /api/stats/today 200
t "stats weekly"                       GET  /api/stats/weekly 200
t "stats monthly"                      GET  /api/stats/monthly 200
t "stats trends"                       GET  /api/stats/trends 200
t "stats summary"                      GET  /api/stats/summary 200
t "gamification streak"                GET  /api/gamification/streak 200
t "gamification achievements"          GET  /api/gamification/achievements 200
t "gamification achievements/me"       GET  /api/gamification/achievements/me 200
t "gamification points"                GET  /api/gamification/points 200
t "push config"                        GET  /api/push/config 200
t "push history"                       GET  /api/push/history 200
t "push stats"                         GET  /api/push/stats 200
t "device connections"                 GET  /api/device/connections 200
t "device config"                      GET  /api/device/config 200
t "device platforms"                   GET  /api/device/platforms 200
t "device platforms metadata"          GET  /api/device/platforms/metadata 200
t "device sync history"                GET  /api/device/sync/history 200
t "device sync rate-limit"             GET  /api/device/sync/rate-limit 200
t "device config status"               GET  /api/device/config/status 200
t "ai chat remaining"                  GET  /api/ai/chat/remaining 200
t "ai chat sessions"                   GET  /api/ai/chat/sessions 200
t "ai parse remaining"                 GET  /api/ai/parse-metrics/remaining 200
t "ai history"                         GET  /api/ai/history 200
t "ocr history"                        GET  /api/ocr/history 200
t "user profile"                       GET  /api/user/profile 200

echo "===== 结果: PASS=$PASS FAIL=$FAIL ====="
cat "$RESULTS"