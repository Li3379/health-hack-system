package com.hhs.service;

import com.hhs.config.TokenEncryptionProperties;
import com.hhs.exception.SystemException;
import com.hhs.service.impl.TokenEncryptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token Encryption Service Unit Tests
 *
 * Tests AES-256-GCM encryption/decryption functionality.
 */
@DisplayName("Token加密服务测试")
class TokenEncryptionServiceTest {

    private TokenEncryptionServiceImpl encryptionService;
    private TokenEncryptionProperties properties;

    // Test key - 32 bytes (256 bits)
    private static final String TEST_KEY_BASE64 = Base64.getEncoder()
        .encodeToString("12345678901234567890123456789012".getBytes());

    @BeforeEach
    void setUp() {
        properties = new TokenEncryptionProperties();
        properties.setKey(TEST_KEY_BASE64);
        encryptionService = new TokenEncryptionServiceImpl(properties);
        encryptionService.init();
    }

    @Test
    @DisplayName("测试1.1：加密有效字符串应返回非空Base64字符串")
    void encrypt_ValidString_ReturnsNonEmptyBase64() {
        // Given
        String plainText = "test-access-token-12345";

        // When
        String encrypted = encryptionService.encrypt(plainText);

        // Then
        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
        // Verify it's valid Base64
        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted));
    }

    @Test
    @DisplayName("测试1.2：相同明文加密两次应产生不同密文（随机IV）")
    void encrypt_SamePlainTextTwice_ProducesDifferentCiphertext() {
        // Given
        String plainText = "same-token-value";

        // When
        String encrypted1 = encryptionService.encrypt(plainText);
        String encrypted2 = encryptionService.encrypt(plainText);

        // Then
        assertNotEquals(encrypted1, encrypted2, "Each encryption should produce different ciphertext due to random IV");
    }

    @Test
    @DisplayName("测试1.3：加密null值应返回null")
    void encrypt_NullInput_ReturnsNull() {
        // When
        String result = encryptionService.encrypt(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("测试1.4：加密空字符串应返回空字符串")
    void encrypt_EmptyInput_ReturnsEmpty() {
        // When
        String result = encryptionService.encrypt("");

        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("测试2.1：解密有效密文应返回原始明文")
    void decrypt_ValidCiphertext_ReturnsOriginalPlainText() {
        // Given
        String originalText = "my-secret-oauth-token-abc123";

        // When
        String encrypted = encryptionService.encrypt(originalText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(originalText, decrypted);
    }

    @Test
    @DisplayName("测试2.2：解密null值应返回null")
    void decrypt_NullInput_ReturnsNull() {
        // When
        String result = encryptionService.decrypt(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("测试2.3：解密空字符串应返回空字符串")
    void decrypt_EmptyInput_ReturnsEmpty() {
        // When
        String result = encryptionService.decrypt("");

        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("测试2.4：解密无效Base64应抛出异常")
    void decrypt_InvalidBase64_ThrowsException() {
        // Given
        String invalidBase64 = "not-valid-base64!!!";

        // When & Then
        assertThrows(SystemException.class, () -> encryptionService.decrypt(invalidBase64));
    }

    @Test
    @DisplayName("测试2.5：解密过短数据应抛出异常")
    void decrypt_TooShortData_ThrowsException() {
        // Given: Data shorter than IV + tag
        String shortData = Base64.getEncoder().encodeToString(new byte[10]);

        // When & Then
        assertThrows(SystemException.class, () -> encryptionService.decrypt(shortData));
    }

    @Test
    @DisplayName("测试2.6：解密被篡改的数据应抛出异常（完整性验证）")
    void decrypt_TamperedData_ThrowsException() {
        // Given
        String originalText = "important-token";
        String encrypted = encryptionService.encrypt(originalText);

        // Tamper with the encrypted data
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[encryptedBytes.length - 1] ^= 0xFF; // Flip last byte
        String tampered = Base64.getEncoder().encodeToString(encryptedBytes);

        // When & Then: GCM should detect tampering
        assertThrows(SystemException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    @DisplayName("测试3.1：使用不同密钥加密的数据无法解密")
    void decrypt_WithWrongKey_ThrowsException() {
        // Given: Encrypt with original key
        String originalText = "secret-data";
        String encrypted = encryptionService.encrypt(originalText);

        // Create new service with different key
        TokenEncryptionProperties wrongProperties = new TokenEncryptionProperties();
        wrongProperties.setKey(Base64.getEncoder().encodeToString(
            "different-key-12345678901234567".getBytes()));
        TokenEncryptionServiceImpl wrongKeyService = new TokenEncryptionServiceImpl(wrongProperties);
        wrongKeyService.init();

        // When & Then: Decryption with wrong key should fail
        assertThrows(SystemException.class, () -> wrongKeyService.decrypt(encrypted));
    }

    @Test
    @DisplayName("测试3.2：服务配置状态检查")
    void isConfigured_WhenKeySet_ReturnsTrue() {
        // When
        boolean configured = encryptionService.isConfigured();

        // Then
        assertTrue(configured);
    }

    @Test
    @DisplayName("测试3.3：未配置密钥时服务不可用")
    void isConfigured_WhenKeyNotSet_ReturnsFalse() {
        // Given
        TokenEncryptionProperties noKeyProperties = new TokenEncryptionProperties();
        noKeyProperties.setKey("");
        TokenEncryptionServiceImpl noKeyService = new TokenEncryptionServiceImpl(noKeyProperties);
        noKeyService.init();

        // When
        boolean configured = noKeyService.isConfigured();

        // Then
        assertFalse(configured);
    }

    @Test
    @DisplayName("测试3.4：未配置时加密应抛出异常")
    void encrypt_WhenNotConfigured_ThrowsException() {
        // Given
        TokenEncryptionProperties noKeyProperties = new TokenEncryptionProperties();
        noKeyProperties.setKey("");
        TokenEncryptionServiceImpl noKeyService = new TokenEncryptionServiceImpl(noKeyProperties);
        noKeyService.init();

        // When & Then
        assertThrows(SystemException.class, () -> noKeyService.encrypt("test"));
    }

    @Test
    @DisplayName("测试4.1：加密长字符串")
    void encrypt_LongString_Success() {
        // Given: Long OAuth token
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("a");
        }
        String longToken = sb.toString();

        // When
        String encrypted = encryptionService.encrypt(longToken);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(longToken, decrypted);
    }

    @Test
    @DisplayName("测试4.2：加密包含特殊字符的字符串")
    void encrypt_SpecialCharacters_Success() {
        // Given: Token with special characters
        String specialToken = "token-with-special-chars!@#$%^&*()_+-=[]{}|;':\",./<>?~`";

        // When
        String encrypted = encryptionService.encrypt(specialToken);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(specialToken, decrypted);
    }

    @Test
    @DisplayName("测试4.3：加密Unicode字符")
    void encrypt_UnicodeCharacters_Success() {
        // Given: Token with Unicode
        String unicodeToken = "token-unicode-\u4e2d\u6587-\u65e5\u672c\u8a9e-\ud55c\uad6d\uc5b4";

        // When
        String encrypted = encryptionService.encrypt(unicodeToken);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(unicodeToken, decrypted);
    }

    @Test
    @DisplayName("测试5.1：多次加密解密循环")
    void encryptDecrypt_MultipleIterations_Success() {
        // Given
        for (int i = 0; i < 10; i++) {
            String plainText = "iteration-" + i + "-token-" + System.nanoTime();

            // When
            String encrypted = encryptionService.encrypt(plainText);
            String decrypted = encryptionService.decrypt(encrypted);

            // Then
            assertEquals(plainText, decrypted, "Failed at iteration " + i);
        }
    }

    @Test
    @DisplayName("测试5.2：密文长度计算正确")
    void encrypted_LengthIsCorrect() {
        // Given
        String plainText = "test-token";
        int plainTextBytes = plainText.getBytes().length;

        // When
        String encrypted = encryptionService.encrypt(plainText);
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);

        // Then: IV(12) + ciphertext + tag(16)
        int expectedMinLength = 12 + plainTextBytes + 16;
        assertEquals(expectedMinLength, encryptedBytes.length);
    }
}