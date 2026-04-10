# 认证与安全

> 本文档描述 HHS 系统的认证机制和安全措施。

## 安全架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         客户端                                   │
│                     (Web / Mobile App)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       API Gateway                                │
│                   (Nginx / Load Balancer)                        │
│  - HTTPS 终止                                                   │
│  - 请求限流                                                     │
│  - IP 黑名单                                                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Security                               │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    JWT Filter                            │    │
│  │  1. 解析 Authorization Header                            │    │
│  │  2. 验证 JWT 签名                                        │    │
│  │  3. 检查 Token 过期                                      │    │
│  │  4. 设置 SecurityContext                                 │    │
│  └─────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                  Authorization                           │    │
│  │  - 角色检查 (ROLE_USER, ROLE_ADMIN)                      │    │
│  │  - 资源权限                                              │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       业务层                                     │
│  - 数据验证                                                     │
│  - 业务权限检查                                                 │
│  - 敏感数据加密                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## JWT 认证

### 安全组件

| 组件 | 类名 | 职责 |
|------|------|------|
| JWT 过滤器 | `JwtAuthenticationFilter` | 请求拦截、Token 解析、认证设置 |
| JWT 工具 | `JwtUtil` | Token 生成、验证、解析 |
| JWT 配置 | `JwtProperties` | JWT 密钥和过期时间配置 |
| JWT 密钥验证 | `JwtSecretValidator` | 启动时校验密钥强度 |
| 安全上下文 | `SecurityUtils` | 获取当前登录用户信息 |
| 认证入口 | `RestAuthenticationEntryPoint` | 处理未认证请求（401） |
| 访问拒绝 | `RestAccessDeniedHandler` | 处理权限不足（403） |
| 用户详情 | `UserDetailsServiceImpl` | Spring Security 用户加载 |
| 路径验证 | `PathValidationUtil` | 路径安全检查（防止路径穿越） |
| 登录用户 | `LoginUser` | 认证用户信息载体 |

### 认证流程

```
1. 用户登录
   POST /api/auth/login { username, password }
        │
        ▼
2. 验证凭据
   - 查询用户
   - BCrypt 密码验证
        │
        ▼
3. 生成 JWT Token
   - 设置 Claims (userId, username, roles)
   - 设置过期时间 (7天)
   - 签名
        │
        ▼
4. 返回 Token
   { token: "eyJhbGciOiJIUzI1NiIs...", user: {...} }
        │
        ▼
5. 后续请求
   Authorization: Bearer <token>
        │
        ▼
6. JWT Filter 验证
   - 解析 Token
   - 验证签名
   - 检查过期
   - 设置认证信息
```

### JWT 配置

```java
@Component
@ConfigurationProperties(prefix = "security.jwt")
@Data
public class JwtProperties {

    private String secret;
    private long expireDays = 7;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET is required but not set.\n" +
                "Expected: 256-bit (32+ character) random string."
            );
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 256 bits (32 characters)."
            );
        }
    }
}
```

### 密钥要求

- **最小长度**：32 字符（256 位）
- **推荐长度**：64 字符（512 位）
- **生成方式**：使用密码学安全的随机生成器

```bash
# 生成安全的 JWT 密钥
openssl rand -base64 64
```

### Token 存储

前端将 Token 存储在 localStorage 或 sessionStorage：

```typescript
// 存储 Token
export const storage = {
  setToken(token: string) {
    localStorage.setItem('token', token)
  },
  getToken(): string | null {
    return localStorage.getItem('token')
  },
  clear() {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }
}
```

### 请求拦截

```typescript
// 请求拦截器：添加 Token
request.interceptors.request.use(config => {
  const token = storage.getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：处理 401
request.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // Token 过期，清除登录状态
      storage.clear()
      router.push('/login')
    }
    return Promise.reject(error)
  }
)
```

## 密码安全

### 密码加密

使用 BCrypt 算法加密密码：

```java
@Service
public class UserService {

    private final PasswordEncoder passwordEncoder;

    public void register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        // ...
        userMapper.insert(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.username());
        
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        
        // 生成 Token
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, toVO(user));
    }
}
```

### 密码规则

- **最小长度**：8 字符
- **复杂度**：建议包含大小写字母、数字、特殊字符
- **哈希算法**：BCrypt（自动加盐）

## 敏感数据加密

### OAuth Token 加密

设备同步的 OAuth Token 使用 AES 加密存储：

```java
@Service
public class TokenEncryptionService {

    @Value("${device.encryption.key}")
    private String encryptionKey;

    private SecretKeySpec getSecretKey() {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey());
            return new String(cipher.doFinal(decoded));
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
```

### 加密配置

```env
# 设备 Token 加密密钥（32 字符）
DEVICE_ENCRYPTION_KEY=your_32_character_encryption_key
```

## 限流保护

### AI 服务限流

```java
@Component
public class AIRateLimiter {

    private static final int USER_DAILY_LIMIT = 20;
    private static final int VISITOR_DAILY_LIMIT = 3;

    public boolean checkLimit(Long userId) {
        String key = userId != null 
            ? "ai:limit:user:" + userId 
            : "ai:limit:visitor:" + getClientIp();
        
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        
        int limit = userId != null ? USER_DAILY_LIMIT : VISITOR_DAILY_LIMIT;
        return count <= limit;
    }
}
```

### 设备同步限流

```java
@Component
public class DeviceSyncRateLimiter {

    private static final Duration SYNC_INTERVAL = Duration.ofMinutes(1);

    public boolean allowSync(Long userId, String platform) {
        String key = String.format("sync:limit:%d:%s", userId, platform);
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(key, "1", SYNC_INTERVAL)
        );
    }
}
```

## CORS 配置

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${allowed.origins:http://localhost}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins.split(","))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

## 安全检查清单

### 必须项

- [ ] JWT_SECRET 至少 32 字符
- [ ] 所有密码使用 BCrypt 加密
- [ ] OAuth Token 加密存储
- [ ] 敏感接口启用认证
- [ ] CORS 配置正确的域名
- [ ] 生产环境使用 HTTPS

### 推荐项

- [ ] 启用请求限流
- [ ] 配置 IP 黑名单
- [ ] 日志脱敏处理
- [ ] 定期更新依赖版本
- [ ] 启用安全扫描

## 环境变量

### 必需变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `JWT_SECRET` | JWT 签名密钥 | 32+ 字符随机字符串 |
| `DB_PASSWORD` | 数据库密码 | 强密码 |
| `REDIS_PASSWORD` | Redis 密码 | 强密码 |

### 可选变量

| 变量 | 说明 |
|------|------|
| `DEVICE_ENCRYPTION_KEY` | 设备 Token 加密密钥 |
| `DASH_SCOPE_API_KEY` | 阿里 AI API 密钥 |
| `HUAWEI_CLIENT_ID` | 华为健康 OAuth |
| `HUAWEI_CLIENT_SECRET` | 华为健康 OAuth |
| `XIAOMI_CLIENT_ID` | 小米健康 OAuth |
| `XIAOMI_CLIENT_SECRET` | 小米健康 OAuth |
| `BAIDU_OCR_API_KEY` | 百度 OCR |
| `BAIDU_OCR_SECRET_KEY` | 百度 OCR |
| `MAIL_HOST` | 邮件推送 SMTP 服务器 |
| `MAIL_PORT` | 邮件推送端口 |
| `MAIL_USERNAME` | 邮件推送发件邮箱 |
| `MAIL_PASSWORD` | 邮件推送授权码 |
| `ALLOWED_ORIGINS` | CORS 允许的源（逗号分隔） |

## 安全最佳实践

### 1. 密钥管理

- 使用环境变量存储密钥
- 不要在代码中硬编码
- 定期轮换密钥
- 使用密钥管理服务（生产环境）

### 2. 输入验证

- 验证所有用户输入
- 使用参数化查询防止 SQL 注入
- 转义输出防止 XSS

### 3. 会话管理

- Token 有效期不宜过长
- 提供登出功能
- 检测异常登录

### 4. 日志安全

- 不要记录敏感信息
- 日志文件权限限制
- 定期归档和清理

## 安全响应

如果发现安全问题：

1. **立即停止**：暂停相关功能
2. **评估影响**：确定受影响的用户和数据
3. **修复漏洞**：尽快修复安全问题
4. **通知用户**：如果涉及用户数据，通知用户
5. **复盘改进**：分析原因，改进安全流程