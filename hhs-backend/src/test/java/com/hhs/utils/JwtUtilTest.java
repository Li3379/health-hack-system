package com.hhs.utils;

import com.hhs.security.JwtUtil;
import com.hhs.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类测试
 * 
 * 这是一个纯工具类测试，不需要 Mock
 *
 */
@DisplayName("JWT工具类测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // 创建测试用的 JwtProperties，密钥必须至少 256 位（32字符）
        JwtProperties testProperties = new JwtProperties();
        testProperties.setSecret("test-secret-key-for-jwt-must-be-at-least-256-bits-long-12345678");
        testProperties.setExpireDays(7);
        
        // 使用 testProperties 初始化 JwtUtil
        jwtUtil = new JwtUtil(testProperties);
        
        // 手动调用 init() 方法（在单元测试中 @PostConstruct 不会自动执行）
        jwtUtil.init();
    }

    @Test
    @DisplayName("测试1：生成Token应该返回非空字符串")
    void generateToken_ValidInput_ReturnsNonEmptyString() {
        // Given: 准备测试数据
        Long userId = 1L;
        String username = "testuser";

        // When: 生成 Token
        String token = jwtUtil.generateToken(userId, username);

        // Then: 验证结果
        assertNotNull(token, "Token 不应该为 null");
        assertFalse(token.isEmpty(), "Token 不应该为空字符串");
        assertTrue(token.contains("."), "JWT Token 应该包含点号分隔符");
    }

    @Test
    @DisplayName("测试2：验证有效Token应该返回true")
    void validateToken_ValidToken_ReturnsTrue() {
        // Given: 生成一个有效Token
        Long userId = 1L;
        String username = "testuser";
        String token = jwtUtil.generateToken(userId, username);

        // When: 验证Token
        boolean isValid = jwtUtil.validateToken(token);

        // Then: 应该有效
        assertTrue(isValid, "刚生成的 Token 应该是有效的");
    }

    @Test
    @DisplayName("测试3：从Token中解析用户ID")
    void getUserIdFromToken_ValidToken_ReturnsCorrectUserId() {
        // Given: 生成Token
        Long expectedUserId = 123L;
        String username = "testuser";
        String token = jwtUtil.generateToken(expectedUserId, username);

        // When: 解析用户ID
        Long actualUserId = jwtUtil.getUserId(token);

        // Then: 应该一致
        assertEquals(expectedUserId, actualUserId, "解析出的用户ID应该与原始ID一致");
    }

    @Test
    @DisplayName("测试4：从Token中解析用户名")
    void getUsernameFromToken_ValidToken_ReturnsCorrectUsername() {
        // Given: 生成Token
        Long userId = 1L;
        String expectedUsername = "testuser";
        String token = jwtUtil.generateToken(userId, expectedUsername);

        // When: 解析用户名
        String actualUsername = jwtUtil.getUsername(token);

        // Then: 应该一致
        assertEquals(expectedUsername, actualUsername, "解析出的用户名应该与原始用户名一致");
    }

    @Test
    @DisplayName("测试5：无效Token应该验证失败")
    void validateToken_InvalidToken_ReturnsFalse() {
        // Given: 一个无效的Token
        String invalidToken = "invalid.token.here";

        // When: 验证Token
        boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then: 应该无效
        assertFalse(isValid, "无效的 Token 验证应该失败");
    }

    @Test
    @DisplayName("测试6：null Token应该验证失败")
    void validateToken_NullToken_ReturnsFalse() {
        // When & Then: null Token 应该返回 false
        assertFalse(jwtUtil.validateToken(null), "null Token 应该验证失败");
    }
}



