package com.hhs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Knife4j (Swagger) API文档配置
 * 访问地址: http://localhost:8082/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI hhsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HHS 健康生活小技巧平台 API")
                        .description("""
                                # Health Hack System 后端接口文档
                                
                                ## 项目简介
                                HHS 是一个健康生活小技巧分享社区平台，用户可以发布、浏览、收藏健康相关的技巧和经验。
                                
                                ## 主要功能模块
                                - **认证模块**: 用户注册、登录
                                - **技巧模块**: 发布、浏览、点赞、收藏健康技巧
                                - **评论模块**: 发表评论、点赞评论
                                - **用户中心**: 个人资料、我的发布、我的收藏
                                - **AI功能**: 智能分类、健康顾问对话
                                - **文件上传**: 头像上传
                                
                                ## 认证说明
                                大部分接口需要在请求头中携带 JWT Token：
                                ```
                                Authorization: Bearer {token}
                                ```
                                
                                ## 访客模式
                                以下接口支持访客（未登录）访问：
                                - GET /api/tips - 技巧列表
                                - GET /api/tips/{id} - 技巧详情
                                - GET /api/tips/{id}/comments - 评论列表
                                - GET /api/users/{id} - 用户信息
                                
                                ## 快速开始
                                1. 使用注册接口创建账号
                                2. 使用登录接口获取 token
                                3. 在右上角"Authorize"按钮中输入 token
                                4. 开始测试其他接口
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("健捕开发团队")
                                .email("3379044054@qq.com")
                                .url("https://github.com/Li3379/HHS"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8082")
                                .description("本地开发环境")
                        // 生产环境配置示例（请根据实际情况修改）：
                        // new Server()
                        //     .url("https://api.yourcompany.com")
                        //     .description("生产环境")
                ))
                .externalDocs(new ExternalDocumentation()
                        .description("📚 项目文档 & GitHub 仓库")
                        .url("https://github.com/Li3379/HHS"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("请输入 JWT Token，格式：Bearer {token}")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication"));
    }
}
