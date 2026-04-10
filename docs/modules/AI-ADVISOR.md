# AI 顾问模块

> 本文档描述 AI 健康顾问模块的功能和实现。

## 模块概述

AI 顾问模块基于阿里通义千问大模型，提供智能健康咨询服务。

## 功能特性

- AI 健康咨询对话
- 个性化健康建议
- 多轮对话上下文
- 会话历史管理
- 内容安全过滤
- 请求限流保护

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端视图层                               │
│                      views/ai/Chat.vue                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         API 层                                   │
│                        api/ai.ts                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       后端控制层                                 │
│                    AIParseController.java                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       服务层                                     │
│  AIChatService │ ContentFilter │ AIRateLimiter                   │
│  ConversationService │ MessageService                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       AI 模型层                                  │
│           LangChain4j → 通义千问 API (OpenAI 兼容)               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       数据层                                     │
│                    ai_conversation                               │
└─────────────────────────────────────────────────────────────────┘
```

## AI 模型配置

### LangChain4j 集成

```java
@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .modelName("qwen3.5-flash")
            .temperature(0.7)
            .maxTokens(2000)  // 开发环境 2000，生产环境 1000
            .timeout(Duration.ofSeconds(60))  // 开发环境 60s，生产环境 30s
            .logRequests(true)
            .logResponses(true)
            .build();
    }
}
```

### 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| model-name | qwen3.5-flash | 模型名称 |
| temperature | 0.7 | 创造性程度 |
| max-tokens | 2000（开发）/ 1000（生产） | 最大输出长度 |
| timeout | 60s（开发）/ 30s（生产） | 请求超时时间 |

> 注意：开发环境和生产环境的配置参数不同，详见 application-dev.yml 和 application-prod.yml。

## 系统提示词

AI 顾问使用预设的系统提示词来定义角色和行为：

```text
你是"小健"，HHS平台的AI健康顾问助手。

你的职责：
1. 提供准确、专业、易懂的健康建议
2. 基于科学依据，不传播伪科学
3. 语气友好、专业、鼓励性
4. 回答简洁，控制在200字以内

重要原则：
- 不提供疾病诊断（建议就医）
- 不推荐具体药品
- 强调个体差异
- 涉及严重健康问题时，建议咨询专业医生

你擅长的领域：
- 饮食营养建议
- 运动健身指导
- 睡眠质量改善
- 心理健康调节
- 日常保健知识
```

## 个性化上下文

AI 对话会自动注入用户的健康档案作为上下文：

```java
private String buildPersonalizedPrompt(Long userId) {
    StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT);
    sb.append("\n\n## 当前用户健康档案\n");
    
    // 添加健康档案信息
    HealthProfileVO profile = healthProfileService.getByUserId(userId);
    if (profile != null) {
        sb.append("**基本信息**:\n");
        sb.append("- 性别: ").append(profile.gender()).append("\n");
        sb.append("- 年龄: ").append(age).append("岁\n");
        // ... 更多信息
    }
    
    // 添加最近健康指标
    List<RealtimeMetric> metrics = realtimeMetricMapper.getLatestMetricsByUser(userId);
    // ... 添加指标信息
    
    return sb.toString();
}
```

## 对话流程

```
1. 用户发送问题
2. 限流检查
3. 内容安全检查
4. 构建个性化提示词
5. 获取对话历史
6. 调用 AI 模型
7. 响应内容过滤
8. 保存对话记录
9. 返回结果
```

## 限流策略

### 限制规则

| 用户类型 | 每日限制 | 说明 |
|----------|----------|------|
| 登录用户 | 20 次 | 每天重置 |
| 访客 | 3 次 | 基于 IP 限制 |

### 实现方式

```java
@Component
public class AIRateLimiter {
    
    public boolean checkLimit(Long userId) {
        String key = userId != null 
            ? "ai:limit:user:" + userId 
            : "ai:limit:visitor:" + getClientIp();
        
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        
        int limit = userId != null ? 20 : 3;
        return count <= limit;
    }
    
    public int getRemainingCount(Long userId) {
        // 返回剩余次数
    }
}
```

## 内容安全

### 输入过滤

```java
@Component
public class ContentFilter {
    
    public boolean containsSensitive(String text) {
        // 检查敏感词
        return sensitiveWords.stream()
            .anyMatch(word -> text.contains(word));
    }
    
    public String cleanInput(String text) {
        // 清理特殊字符
        return text.trim()
            .replaceAll("[<>]", "")
            .replaceAll("javascript:", "");
    }
}
```

### 输出过滤

```java
public String filterResponse(String response) {
    // 移除可能的敏感内容
    // 添加安全提示
    return response;
}
```

## API 接口

### 对话接口

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/ai/chat` | 发送对话消息 |
| GET | `/api/ai/history` | 获取对话历史 |
| DELETE | `/api/ai/session/{id}` | 清空会话 |
| GET | `/api/ai/sessions` | 获取会话列表 |

### 请求示例

```json
// POST /api/ai/chat
{
  "sessionId": "uuid-session-id",
  "question": "最近睡眠不好怎么办？"
}
```

### 响应示例

```json
{
  "code": 200,
  "data": {
    "answer": "您好！睡眠问题很常见，以下是一些改善建议...",
    "sessionId": "uuid-session-id",
    "remaining": 19
  }
}
```

## 会话管理

### 会话创建

- 首次对话自动创建新会话
- 返回 sessionId 用于后续对话
- 会话持久化到数据库

### 对话历史

- 每个会话保存完整对话记录
- 支持分页查询历史
- 最近 5 轮对话作为上下文

```java
public List<ChatMessage> getRecentHistory(String sessionId, int rounds) {
    List<AIConversation> conversations = conversationService
        .getRecentBySession(sessionId, rounds);
    
    return conversations.stream()
        .flatMap(c -> Stream.of(
            UserMessage.from(c.getQuestion()),
            AiMessage.from(c.getAnswer())
        ))
        .collect(Collectors.toList());
}
```

## 前端实现

### Chat.vue

```vue
<template>
  <div class="ai-chat">
    <!-- 消息列表 -->
    <div class="messages">
      <div v-for="msg in messages" :class="msg.role">
        {{ msg.content }}
      </div>
    </div>
    
    <!-- 输入框 -->
    <el-input 
      v-model="input" 
      @keyup.enter="send"
      placeholder="输入您的健康问题..."
    />
  </div>
</template>

<script setup lang="ts">
const messages = ref<Message[]>([])
const input = ref('')
const sessionId = ref(uuid())

const send = async () => {
  if (!input.value.trim()) return
  
  // 添加用户消息
  messages.value.push({ role: 'user', content: input.value })
  
  // 调用 API
  const res = await aiApi.chat({
    sessionId: sessionId.value,
    question: input.value
  })
  
  // 添加 AI 响应
  messages.value.push({ role: 'assistant', content: res.data.answer })
  
  input.value = ''
}
</script>
```

## 错误处理

### 常见错误

| 错误码 | 说明 | 处理方式 |
|--------|------|----------|
| AI_RATE_LIMIT_EXCEEDED | 超过限流 | 提示用户明天再试 |
| AI_ERROR | AI 服务异常 | 提示稍后重试 |
| VALIDATION_INVALID_PARAMETER | 内容敏感 | 提示重新表述 |

### 降级策略

当 AI 服务不可用时：

```java
if (!StringUtils.hasText(apiKey)) {
    return new ChatLanguageModel() {
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from("AI服务未配置，请联系管理员。"));
        }
    };
}
```

## 监控指标

- 对话成功率
- 平均响应时间
- Token 使用量
- 限流触发次数

## 注意事项

1. **API Key 安全**：不要在代码中硬编码，使用环境变量
2. **响应超时**：设置合理的超时时间，避免长时间等待
3. **Token 计费**：监控 Token 使用量，控制成本
4. **内容审核**：确保输出内容符合规范