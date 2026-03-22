# 设备同步配置指南

本指南详细说明如何配置各个穿戴设备平台的 API 凭据，以实现健康数据同步功能。

---

## 目录

1. [环境变量概览](#环境变量概览)
2. [华为运动健康](#1-华为运动健康-huawei)
3. [小米运动](#2-小米运动-xiaomi)
4. [微信运动](#3-微信运动-wechat)
5. [Apple Health](#4-apple-health-apple)
6. [加密密钥配置](#加密密钥配置)
7. [常见问题](#常见问题)

---

## 环境变量概览

| 环境变量 | 用途 | 必需 |
|---------|------|------|
| `DEVICE_ENCRYPTION_KEY` | Token 加密密钥 (32字节) | ✅ 是 |
| `HUAWEI_CLIENT_ID` | 华为 OAuth 客户端 ID | 可选 |
| `HUAWEI_CLIENT_SECRET` | 华为 OAuth 客户端密钥 | 可选 |
| `XIAOMI_CLIENT_ID` | 小米 OAuth 客户端 ID | 可选 |
| `XIAOMI_CLIENT_SECRET` | 小米 OAuth 客户端密钥 | 可选 |
| `BASE_URL` | 应用基础 URL (用于回调) | 可选 |

---

## 1. 华为运动健康 (Huawei)

### 1.1 注册开发者账号

1. 访问 **华为开发者联盟**: https://developer.huawei.com
2. 点击「注册」，使用华为账号登录
3. 完成开发者实名认证

### 1.2 创建项目和应用

1. 进入 **AppGallery Connect** 控制台: https://console.huawei.com/consumer
2. 点击「我的项目」→「创建项目」
3. 填写项目名称，选择项目类型为「健康运动」
4. 在项目下「添加应用」，选择「移动应用」或「Web应用」

### 1.3 开启华为运动健康服务

1. 在应用详情页，找到「API管理」
2. 搜索并开启以下 API：
   - **Health Kit** - 健康数据服务
   - **HUAWEI Health Kit API** - 运动健康数据接口

3. 在「增长」→「流量分发」中配置 OAuth 2.0：
   - 添加授权回调地址：`https://your-domain.com/api/device/callback/huawei`

### 1.4 获取凭据

1. 在应用详情页的「项目设置」中找到：
   - **应用ID (App ID)** → 这就是 `HUAWEI_CLIENT_ID`
   - **应用密钥 (App Secret)** → 这就是 `HUAWEI_CLIENT_SECRET`

### 1.5 配置权限范围 (Scopes)

确保申请以下权限范围：

| Scope | 说明 |
|-------|------|
| `https://www.huawei.com/healthkit/healthdata.read` | 读取健康数据 |
| `https://www.huawei.com/healthkit/healthdata.write` | 写入健康数据 |

### 1.6 文档参考

- 华为 Health Kit 开发文档: https://developer.huawei.com/consumer/cn/hms/huaweihealthkit
- OAuth 2.0 接入指南: https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/health-sdk-development-process

---

## 2. 小米运动 (Xiaomi)

### 2.1 注册开发者账号

1. 访问 **小米开放平台**: https://dev.mi.com
2. 使用小米账号登录
3. 完成开发者认证

### 2.2 创建应用

1. 进入「控制台」→「应用管理」
2. 点击「创建应用」
3. 选择应用类型为「健康运动类」
4. 填写应用名称和描述

### 2.3 开启运动健康 API

1. 在应用详情页，申请开通「小米运动健康 API」
2. 配置 OAuth 回调地址：`https://your-domain.com/api/device/callback/xiaomi`

### 2.4 获取凭据

在应用详情页获取：
- **App ID** → `XIAOMI_CLIENT_ID`
- **App Secret** → `XIAOMI_CLIENT_SECRET`

### 2.5 文档参考

- 小米开放平台文档: https://dev.mi.com/console/doc/detail?pId=2309
- 运动健康 API 接入指南: https://dev.mi.com/console/doc/detail?pId=2310

---

## 3. 微信运动 (WeChat)

> ⚠️ **注意**: 微信运动目前不提供公开的 OAuth API，数据获取需通过以下方式：
> - 微信小程序 + 运动数据接口
> - 微信开放平台的「微信运动」数据服务（需申请）

### 3.1 微信小程序方式

1. 注册微信小程序账号: https://mp.weixin.qq.com
2. 在小程序后台开通「微信运动」数据权限
3. 使用 `wx.getWeRunData()` API 获取加密数据
4. 后端解密获取步数等数据

### 3.2 微信开放平台方式

1. 访问微信开放平台: https://open.weixin.qq.com
2. 创建网站应用或移动应用
3. 申请「微信运动数据」接口权限（需审核）

### 3.3 文档参考

- 微信小程序运动数据: https://developers.weixin.qq.com/miniprogram/dev/api/open-api/werun/wx.getWeRunData.html
- 微信开放平台: https://open.weixin.qq.com/cgi-bin/showdocument?action=dir_list&t=resource/res_list

---

## 4. Apple Health (Apple)

> ⚠️ **注意**: Apple Health 数据获取需要 iOS 应用，不支持纯 Web OAuth 流程。

### 4.1 开发者计划

1. 加入 **Apple Developer Program**: https://developer.apple.com/programs
2. 年费: $99/年

### 4.2 启用 HealthKit

1. 在 Xcode 项目中，选择项目的 Target
2. 进入「Signing & Capabilities」
3. 点击「+ Capability」添加 **HealthKit**
4. 配置所需的健康数据类型权限

### 4.3 数据同步方案

由于 Apple 不提供 Web API，推荐以下方案：

| 方案 | 说明 | 适用场景 |
|------|------|---------|
| **iOS 应用** | 原生 HealthKit 框架 | 有 iOS 开发能力 |
| **第三方服务** | 通过 HealthKit→云服务同步 | 需要中间件 |
| **手动导入** | 导出 XML/CSV 后手动上传 | 临时方案 |

### 4.4 文档参考

- HealthKit 开发文档: https://developer.apple.com/documentation/healthkit
- HealthKit 数据类型: https://developer.apple.com/documentation/healthkit/data_types

---

## 加密密钥配置

### 生成加密密钥

Token 加密使用 **AES-256-GCM** 算法，需要 32 字节（256位）的密钥。

#### 方法一：OpenSSL 生成（推荐）

```bash
# Linux/Mac
openssl rand -base64 32

# Windows (PowerShell)
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

#### 方法二：在线工具

访问 https://www.uuidgenerator.net/api/guid 生成随机字符串，确保长度为 32 字符。

#### 方法三：手动创建

创建一个 32 字符的随机字符串（包含大小写字母、数字、特殊字符）。

### 配置密钥

```bash
# Linux/Mac
export DEVICE_ENCRYPTION_KEY="your-32-byte-encryption-key-here"

# Windows PowerShell
$env:DEVICE_ENCRYPTION_KEY = "your-32-byte-encryption-key-here"

# Windows CMD
set DEVICE_ENCRYPTION_KEY=your-32-byte-encryption-key-here
```

---

## 配置示例

### 开发环境 (application-dev.yml)

```yaml
device:
  encryption:
    key: ${DEVICE_ENCRYPTION_KEY:dev-test-encryption-key-32bytes!}
  mock:
    enabled: true
  oauth:
    platforms:
      huawei:
        client-id: ${HUAWEI_CLIENT_ID:dev-test-client-id}
        client-secret: ${HUAWEI_CLIENT_SECRET:dev-test-client-secret}
        auth-url: https://oauth-login.cloud.huawei.com/oauth2/v2/authorize
        token-url: https://oauth-login.cloud.huawei.com/oauth2/v2/token
        redirect-uri: ${BASE_URL:http://localhost:8082}/api/device/callback/huawei
        scopes:
          - https://www.huawei.com/healthkit/healthdata.read
          - https://www.huawei.com/healthkit/healthdata.write
```

### 生产环境 (application-prod.yml)

```yaml
device:
  encryption:
    key: ${DEVICE_ENCRYPTION_KEY}  # 必须设置环境变量，无默认值
  mock:
    enabled: false  # 生产环境禁用模拟数据
  oauth:
    platforms:
      huawei:
        client-id: ${HUAWEI_CLIENT_ID}  # 必须设置真实凭据
        client-secret: ${HUAWEI_CLIENT_SECRET}
        auth-url: https://oauth-login.cloud.huawei.com/oauth2/v2/authorize
        token-url: https://oauth-login.cloud.huawei.com/oauth2/v2/token
        redirect-uri: ${BASE_URL}/api/device/callback/huawei
        scopes:
          - https://www.huawei.com/healthkit/healthdata.read
          - https://www.huawei.com/healthkit/healthdata.write
```

---

## 常见问题

### Q1: 提示 "平台未配置"

**原因**: 缺少必要的凭据配置

**解决方案**:
1. 检查环境变量是否正确设置
2. 确认 `DEVICE_ENCRYPTION_KEY` 长度为 32 字节
3. 重启应用后检查启动日志

### Q2: OAuth 授权后无数据

**原因**:
- API 权限范围未正确配置
- 用户未授权相应权限

**解决方案**:
1. 检查 Scopes 配置是否包含所需权限
2. 在授权页面确认用户勾选了所有权限
3. 检查 API 返回的错误信息

### Q3: Token 加密失败

**原因**: 加密密钥格式不正确

**解决方案**:
1. 确保密钥长度为 32 字节
2. 使用 Base64 编码或原始字符串格式
3. 检查日志中的具体错误信息

### Q4: 华为 API 返回错误

**常见错误码**:

| 错误码 | 说明 | 解决方案 |
|-------|------|---------|
| 1001 | App ID 无效 | 检查 CLIENT_ID 配置 |
| 1002 | App Secret 错误 | 检查 CLIENT_SECRET 配置 |
| 1003 | 回调地址不匹配 | 确认 redirect_uri 配置 |
| 2001 | 权限未授权 | 检查 Scopes 配置 |

---

## 支持的健康数据类型

| 数据类型 | 指标键 | 单位 | 支持平台 |
|---------|-------|------|---------|
| 心率 | `heart_rate` | bpm | 华为、小米、Apple |
| 步数 | `step_count` | 步 | 全部 |
| 睡眠时长 | `sleep` | 小时 | 华为、小米、Apple |
| 血压（收缩压）| `blood_pressure_systolic` | mmHg | 华为、小米、Apple |
| 血压（舒张压）| `blood_pressure_diastolic` | mmHg | 华为、小米、Apple |
| 血糖 | `blood_glucose` | mg/dL | 华为、小米、Apple |
| 血氧 | `spo2` | % | 华为、小米、Apple |

---

## 技术支持

如遇到配置问题，请检查以下资源：

- **华为开发者论坛**: https://developer.huawei.com/consumer/cn/forum
- **小米开发者社区**: https://dev.mi.com/community
- **微信开放社区**: https://developers.weixin.qq.com/community
- **Apple Developer Forums**: https://developer.apple.com/forums

---

*最后更新: 2026-03-19*