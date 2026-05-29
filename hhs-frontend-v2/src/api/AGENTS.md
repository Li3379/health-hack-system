<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# api/ — API 调用模块

**Purpose**: API 调用模块，每个文件对应一个后端 controller，封装所有 HTTP 请求。

## Key Files

所有文件使用 `request` from `@/utils/request.ts`：

| File | Purpose | Symbols |
|------|---------|---------|
| `ai.ts` | AI 分析 API | - |
| `ai-parse.ts` | AI 解析 API | 7 |
| `alert.ts` | 告警 API | - |
| `auth.ts` | 认证 API (登录/注册/登出) | - |
| `device.ts` | 设备管理 API (含同步、绑定) | 9 |
| `goals.ts` | 目标管理 API | 7 |
| `health.ts` | 健康数据 API | - |
| `mood.ts` | 心情追踪 API | 6 |
| `ocr.ts` | OCR 识别 API | 7 |
| `prevention.ts` | 预防保健 API | - |
| `push.ts` | 推送配置 API (含渠道管理) | 12 |
| `realtime.ts` | 实时监控 API | - |
| `reminders.ts` | 提醒管理 API | 5 |
| `score.ts` | 健康评分 API | - |
| `screening.ts` | 筛查 API | - |
| `screening-compare.ts` | 筛查对比 API | 7 |
| `stats.ts` | 统计数据 API | 7 |
| `threshold.ts` | 阈值配置 API | - |
| `user.ts` | 用户管理 API | - |
| `wellness.ts` | 健康生活方式 API | - |

## For AI Agents

- 所有函数返回 `Promise<ApiResponse<T>>`
- 使用 `request.get/post/put/delete` 方法
- 新增 API: 创建 `.ts` 文件，导入 `request` from `@/utils/request.ts`
- API 路径与后端 `@RequestMapping` 一致
- 参考现有文件的命名和结构风格
