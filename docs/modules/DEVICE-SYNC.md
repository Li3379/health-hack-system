# 设备同步模块

> 本文档描述可穿戴设备数据同步模块的功能和实现。

## 模块概述

设备同步模块支持从华为健康、小米健康等可穿戴设备平台同步健康数据。

## 功能特性

- 多平台支持（华为、小米）
- OAuth 2.0 安全授权
- 自动/手动同步
- Token 自动刷新
- 数据加密存储

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端视图层                               │
│  DeviceSync.vue │ DeviceConfigWizard.vue │ OAuthCallback.vue    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         API 层                                   │
│                    api/device.ts                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       后端控制层                                 │
│  DevicePlatformConfigController │ DevicePlatformConfigAdminCtrl │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       服务层                                     │
│  DeviceConnectionService │ DevicePlatformService                │
│  DeviceSyncOrchestrationService │ TokenEncryptionService        │
│  XiaomiHealthService                                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       数据层                                     │
│  device_connection │ device_platform_config │ sync_history      │
└─────────────────────────────────────────────────────────────────┘
```

## 支持平台

| 平台 | 平台标识 | 数据类型 | 状态 |
|------|----------|----------|------|
| 华为健康 | huawei | 心率、血压、步数、睡眠 | 已支持 |
| 小米健康 | xiaomi | 心率、步数、睡眠、体重 | 已支持 |
| 微信运动 | wechat | 步数 | 规划中 |
| Apple 健康 | apple | 多种指标 | 规划中 |

## OAuth 授权流程

```
┌─────────┐     ┌─────────┐     ┌─────────────┐     ┌─────────┐
│  用户   │────>│  前端   │────>│   后端      │────>│ 设备平台│
│         │     │         │     │             │     │         │
└─────────┘     └─────────┘     └─────────────┘     └─────────┘
     │               │                   │               │
     │  点击连接设备  │                   │               │
     │──────────────>│                   │               │
     │               │  获取授权 URL     │               │
     │               │──────────────────>│               │
     │               │                   │  构建 OAuth URL│
     │               │                   │──────────────>│
     │               │<──────────────────│               │
     │               │  返回授权页面 URL  │               │
     │<──────────────│                   │               │
     │  跳转授权页面  │                   │               │
     │──────────────────────────────────────────────────>│
     │               │                   │               │
     │  用户授权      │                   │               │
     │<─────────────────────────────────────────────────│
     │  重定向到回调页│                   │               │
     │──────────────>│                   │               │
     │               │  发送授权码       │               │
     │               │──────────────────>│               │
     │               │                   │  换取 Token   │
     │               │                   │──────────────>│
     │               │                   │<──────────────│
     │               │                   │  返回 Token   │
     │               │<──────────────────│               │
     │               │  连接成功         │               │
     │<──────────────│                   │               │
```

## 数据库表

### device_platform_config（平台配置表）

存储各平台的 OAuth 凭据：

| 字段 | 说明 |
|------|------|
| platform | 平台标识（主键） |
| client_id | OAuth Client ID（加密） |
| client_secret | OAuth Client Secret（加密） |
| auth_url | 授权 URL |
| token_url | Token URL |
| redirect_uri | 回调 URL |
| scopes | 权限范围 |
| enabled | 是否启用 |

### device_connection（设备连接表）

存储用户的设备连接信息：

| 字段 | 说明 |
|------|------|
| user_id | 用户 ID |
| platform | 平台标识 |
| access_token | Access Token（加密） |
| refresh_token | Refresh Token（加密） |
| token_expire_at | Token 过期时间 |
| last_sync_at | 最后同步时间 |
| status | 连接状态 |

### sync_history（同步历史表）

记录每次同步的详细信息：

| 字段 | 说明 |
|------|------|
| user_id | 用户 ID |
| platform | 平台标识 |
| sync_type | 同步类型 |
| metrics_count | 同步指标数量 |
| status | 同步状态 |
| duration_ms | 耗时 |

## Token 加密

OAuth Token 使用 AES 加密存储：

```java
@Service
public class TokenEncryptionService {

    @Value("${device.encryption.key}")
    private String encryptionKey;

    public String encrypt(String plainText) {
        // AES 加密
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        byte[] encrypted = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String encryptedText) {
        // AES 解密
        byte[] decoded = Base64.getDecoder().decode(encryptedText);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey());
        return new String(cipher.doFinal(decoded));
    }
}
```

## 同步服务

### 同步编排

```java
@Service
public class DeviceSyncOrchestrationService {

    public SyncResult sync(Long userId, String platform) {
        // 1. 获取连接信息
        DeviceConnection connection = connectionService.getConnection(userId, platform);
        
        // 2. 检查 Token 有效性
        if (isTokenExpired(connection)) {
            refreshToken(connection);
        }
        
        // 3. 拉取数据
        List<HealthMetric> metrics = fetchMetrics(connection);
        
        // 4. 去重处理
        metrics = deduplicate(metrics);
        
        // 5. 保存数据
        saveMetrics(userId, metrics);
        
        // 6. 更新同步时间
        connectionService.updateLastSyncAt(userId, platform);
        
        return new SyncResult(metrics.size());
    }
}
```

### 小米健康同步

```java
@Service
public class XiaomiHealthService {

    public List<HealthMetric> fetchMetrics(String accessToken, LocalDateTime since) {
        // 调用小米健康 API
        String url = "https://api.health.xiaomi.com/v1/sport/step";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        ResponseEntity<XiaomiStepData> response = restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), XiaomiStepData.class);
        
        // 转换为标准格式
        return convertToMetrics(response.getBody());
    }
}
```

## API 接口

### 设备连接管理

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/device/connections` | 获取连接列表 |
| GET | `/api/device/connections/{platform}` | 获取指定平台连接 |
| POST | `/api/device/connections/{platform}/connect` | 发起连接 |
| DELETE | `/api/device/connections/{platform}` | 断开连接 |
| POST | `/api/device/connections/{platform}/sync` | 手动同步 |

### 平台配置管理（管理员）

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/admin/device-platform/configs` | 获取所有配置 |
| PUT | `/api/admin/device-platform/configs/{platform}` | 更新配置 |
| POST | `/api/admin/device-platform/configs/{platform}/test` | 测试配置 |

## 前端组件

### DeviceSync.vue

设备同步管理页面：

- 已连接设备列表
- 连接状态显示
- 手动同步按钮
- 断开连接操作

### DeviceConfigWizard.vue

设备配置向导：

- 选择设备平台
- 配置 OAuth 凭据
- 测试连接
- 保存配置

### OAuthCallback.vue

OAuth 回调处理：

- 解析授权码
- 完成授权流程
- 显示结果

## 限流策略

| 操作 | 限制 |
|------|------|
| 手动同步 | 1 次/分钟 |
| Token 刷新 | 5 次/小时 |

```java
@Component
public class DeviceSyncRateLimiter {

    public boolean allowSync(Long userId, String platform) {
        String key = String.format("sync:limit:%d:%s", userId, platform);
        return redisTemplate.opsForValue()
            .setIfAbsent(key, "1", Duration.ofMinutes(1));
    }
}
```

## 错误处理

### 常见错误

| 错误 | 说明 | 处理方式 |
|------|------|----------|
| TOKEN_EXPIRED | Token 过期 | 自动刷新 Token |
| TOKEN_REVOKED | Token 已撤销 | 提示重新授权 |
| RATE_LIMITED | 请求频率过高 | 等待后重试 |
| NETWORK_ERROR | 网络错误 | 稍后重试 |

## 安全考虑

1. **Token 加密**：所有 Token 使用 AES 加密存储
2. **HTTPS**：所有 API 调用使用 HTTPS
3. **最小权限**：只请求必要的数据权限
4. **定期刷新**：Token 过期前自动刷新

## 监控指标

- 连接成功率
- 同步成功率
- 平均同步时间
- Token 刷新次数

## 注意事项

1. **API 配额**：各平台有 API 调用限制，注意控制频率
2. **数据一致性**：同步时需处理重复数据
3. **Token 管理**：及时刷新即将过期的 Token
4. **错误重试**：网络错误时实现指数退避重试