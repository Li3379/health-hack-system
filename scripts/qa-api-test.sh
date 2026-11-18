#!/usr/bin/env bash
# HHS 全功能白盒 API 测试
BASE=http://localhost:8082
TOKEN=$(cat /tmp/hhs_token.txt)
PASS=0; FAIL=0; RESULTS=/tmp/hhs-qa-results.txt
: > "$RESULTS"

t() { # t <name> <method> <path> <expect-code> [body]
  local name=$1 method=$2 path=$3 expect=$4 body=$5
  local resp code
  if [ -n "$body" ]; then
    resp=$(curl -s -m 20 -X "$method" "$BASE$path" -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" -d "$body" 2>&1)
  else
    resp=$(curl -s -m 20 -X "$method" "$BASE$path" -H "Authorization: Bearer $TOKEN" 2>&1)
  fi
  code=$(printf '%s' "$resp" | python -c "import sys,json
try:
    d=json.load(sys.stdin); print(d.get('code','NOCODE'))
except Exception: print('PARSE_ERR')" 2>/dev/null)
  if [ "$code" = "$expect" ]; then
    PASS=$((PASS+1)); printf 'PASS | %-55s | %s\n' "$name" "$code" >> "$RESULTS"
  else
    FAIL=$((FAIL+1)); printf 'FAIL | %-55s | got=%s want=%s | %s\n' "$name" "$code" "$expect" "$(printf '%s' "$resp" | head -c 220)" >> "$RESULTS"
  fi
}

# ---------- 认证 / 用户 ----------
t "auth/register 缺字段校验"      POST /api/auth/register 400 '{"username":"x"}'
t "auth/register 密码过短"        POST /api/auth/register 400 '{"username":"qa_short","password":"123"}'
t "auth/login 不存在用户"         POST /api/auth/login 500 '{"username":"nobody_zzz","password":"whatever1"}'
t "user/profile 获取"             GET  /api/user/profile 200
t "user/profile 更新昵称"         PUT  /api/user/profile 200 '{"nickname":"QAWB-Test"}'
t "user/profile 非法邮箱"         PUT  /api/user/profile 400 '{"email":"not-an-email"}'
t "users/{id} 公开资料"           GET  /api/users/1 200
t "users/{id} 不存在"             GET  /api/users/999999 200
t "user/password 旧密码错误"      PUT  /api/user/password 500 '{"oldPassword":"wrongpass","newPassword":"newpass123"}'

# ---------- 健康指标 ----------
t "metrics 列表"                  GET  /api/metrics 200
t "metrics 记录血压"              POST /api/metrics 200 '{"metricKey":"systolicBloodPressure","value":118,"recordDate":"2026-11-16","unit":"mmHg"}'
t "metrics 记录体温"              POST /api/metrics 200 '{"metricKey":"bodyTemperature","value":36.5,"recordDate":"2026-11-16","unit":"°C"}'
t "metrics 缺 metricKey"          POST /api/metrics 400 '{"value":1,"recordDate":"2026-11-16"}'
t "metrics 负值"                  POST /api/metrics 400 '{"metricKey":"heartRate","value":-1,"recordDate":"2026-11-16"}'
t "metrics 未来日期"              POST /api/metrics 400 '{"metricKey":"heartRate","value":70,"recordDate":"2030-01-01"}'
t "metrics 非法 key"              POST /api/metrics 400 '{"metricKey":"bad key!","value":70,"recordDate":"2026-11-16"}'
t "metrics/metadata 元数据"       GET  /api/metrics/metadata 200
t "health/score 健康分"           GET  /api/health/score 200
t "health/score/breakdown"        GET  /api/health/score/breakdown 200
t "health/score/history"          GET  /api/health/score/history 200
t "health/score/recalculate"      POST /api/health/score/recalculate 200
t "health/report 报告"            GET  /api/health/report 200
t "health/report/history"         GET  /api/health/report/history 200

# ---------- 预警 ----------
t "alerts 列表"                   GET  /api/alerts 200
t "alerts unread-count"           GET  /api/alerts/unread-count 200
t "alerts recent"                 GET  /api/alerts/recent 200
t "alerts statistics"             GET  /api/alerts/statistics 200
t "alerts read-all"               PUT  /api/alerts/read-all 200
t "alerts trend 血压"             GET  /api/alerts/trend/systolicBloodPressure 200
t "alerts early-warning"          GET  /api/alerts/trend/systolicBloodPressure/early-warning 200
t "alerts 不存在 id read"         PUT  /api/alerts/999999/read 200

# ---------- 目标 ----------
t "goals 列表"                    GET  /api/goals 200
t "goals 创建"                    POST /api/goals 200 '{"title":"QA目标","description":"t","metricKey":"steps","targetValue":10000,"unit":"步","startDate":"2026-11-01","endDate":"2026-12-31"}'
t "goals 创建缺标题"              POST /api/goals 400 '{"metricKey":"steps","targetValue":100,"startDate":"2026-11-01"}'
t "goals 目标值<=0"               POST /api/goals 400 '{"title":"bad","metricKey":"steps","targetValue":0,"startDate":"2026-11-01"}'

# ---------- 心情 ----------
t "mood 记录"                     POST /api/mood/entries 200 '{"moodScore":8,"energyLevel":7,"stressLevel":3,"sleepQuality":8,"notes":"QA","entryDate":"2026-11-16"}'
t "mood 评分越界"                 POST /api/mood/entries 400 '{"moodScore":99,"entryDate":"2026-11-16"}'
t "mood 缺评分"                   POST /api/mood/entries 400 '{"entryDate":"2026-11-16"}'
t "mood 历史"                     GET  /api/mood/history 200
t "mood insights"                 GET  /api/mood/insights 200

# ---------- 提醒 ----------
t "reminders 列表"                GET  /api/reminders 200
t "reminders 创建"                POST /api/reminders 200 '{"title":"QA提醒","reminderType":"custom","cronExpression":"0 0 9 * * ?"}'
t "reminders 缺标题"              POST /api/reminders 400 '{"reminderType":"custom","cronExpression":"0 0 9 * * ?"}'

# ---------- 阈值 ----------
t "thresholds 列表"               GET  /api/thresholds 200
t "thresholds by-key"             GET  /api/thresholds/by-key?metricKey=heartRate 200
t "thresholds 创建"               POST /api/thresholds 200 '{"userId":7,"metricKey":"heartRateQA","warningHigh":100,"criticalHigh":120,"warningLow":50,"criticalLow":40}'
t "thresholds 缺 userId"          POST /api/thresholds 400 '{"metricKey":"heartRate"}'

# ---------- 养生 wellness ----------
t "wellness 列表"                 GET  /api/wellness 200
t "wellness 记录步数"             POST /api/wellness 200 '{"metricKey":"steps","value":8000,"recordDate":"2026-11-16","unit":"步"}'
t "wellness 非法类型"             POST /api/wellness 400 '{"metricKey":"bloodPressure","value":1,"recordDate":"2026-11-16"}'
t "wellness summary"              GET  /api/wellness/summary 200
t "wellness latest"               GET  /api/wellness/latest 200
t "wellness trend steps"          GET  /api/wellness/trend/steps 200

# ---------- 实时 ----------
t "realtime 记录"                 POST /api/realtime/metrics 200 '{"metricKey":"heartRate","value":72,"unit":"bpm","source":"manual"}'
t "realtime 超上限"               POST /api/realtime/metrics 400 '{"metricKey":"heartRate","value":5000}'
t "realtime 列表"                 GET  /api/realtime/metrics 200
t "realtime latest"               GET  /api/realtime/metrics/latest 200
t "realtime history"              GET  /api/realtime/metrics/history 200
t "realtime trend"                GET  /api/realtime/metrics/trend?metricKey=heartRate 200

# ---------- 预防 ----------
t "prevention profile"            GET  /api/prevention/profile 200
t "prevention profile completeness" GET /api/prevention/profile/completeness 200
t "prevention metrics"            GET  /api/prevention/metrics 200
t "prevention risk-assessment"    GET  /api/prevention/risk-assessment 200
t "prevention health-profile"     GET  /api/prevention/health-profile 200

# ---------- 筛查 ----------
t "screening reports"             GET  /api/screening/reports 200

# ---------- 统计 ----------
t "stats today"                   GET  /api/stats/today 200
t "stats weekly"                  GET  /api/stats/weekly 200
t "stats monthly"                 GET  /api/stats/monthly 200
t "stats trends"                  GET  /api/stats/trends 200
t "stats summary"                 GET  /api/stats/summary 200

# ---------- 游戏化 ----------
t "gamification streak"           GET  /api/gamification/streak 200
t "gamification achievements"     GET  /api/gamification/achievements 200
t "gamification achievements/me"  GET  /api/gamification/achievements/me 200
t "gamification points"           GET  /api/gamification/points 200

# ---------- 推送 ----------
t "push config"                   GET  /api/push/config 200
t "push history"                  GET  /api/push/history 200
t "push stats"                    GET  /api/push/stats 200
t "push config 非法渠道"          GET  /api/push/config/invalid_channel 400

# ---------- 设备 ----------
t "device connections"            GET  /api/device/connections 200
t "device config"                 GET  /api/device/config 200
t "device platforms"              GET  /api/device/platforms 200
t "device platforms metadata"     GET  /api/device/platforms/metadata 200
t "device sync history"           GET  /api/device/sync/history 200
t "device sync rate-limit"        GET  /api/device/sync/rate-limit 200
t "device 非法平台"               GET  /api/device/platforms/badplatform/availability 400
t "device config status"          GET  /api/device/config/status 200

# ---------- AI ----------
t "ai chat remaining"             GET  /api/ai/chat/remaining 200
t "ai chat sessions"              GET  /api/ai/chat/sessions 200
t "ai chat 空消息"                POST /api/ai/chat 400 '{"message":""}'
t "ai parse remaining"            GET  /api/ai/parse-metrics/remaining 200
t "ai history"                    GET  /api/ai/history 200
t "ai classify"                   POST /api/ai/classify 200 '{"content":"今天血压有点高","category":"health"}'

# ---------- 导出 ----------
t "export 创建非法类型"           POST /api/exports 400 '{"exportType":"bogus","contentType":"json"}'
t "export 不存在 id"              GET  /api/exports/999999 200

# ---------- OCR ----------
t "ocr history"                   GET  /api/ocr/history 200
t "ocr 不存在 record"             GET  /api/ocr/history/999999 200

# ---------- 鉴权 ----------
noauth=$(curl -s -m 10 -o /dev/null -w "%{http_code}" "$BASE/api/user/profile")
if [ "$noauth" = "401" ] || [ "$noauth" = "403" ]; then
  PASS=$((PASS+1)); echo "PASS | 未认证访问 user/profile 被拒绝 | HTTP $noauth" >> "$RESULTS"
else
  FAIL=$((FAIL+1)); echo "FAIL | 未认证访问 user/profile 未拒绝 | HTTP $noauth" >> "$RESULTS"
fi

echo "===== 结果: PASS=$PASS FAIL=$FAIL ====="
cat "$RESULTS"