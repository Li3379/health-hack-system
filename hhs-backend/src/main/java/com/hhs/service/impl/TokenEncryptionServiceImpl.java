package com.hhs.service.impl;

import com.hhs.common.constant.ErrorCode;
import com.hhs.config.TokenEncryptionProperties;
import com.hhs.exception.SystemException;
import com.hhs.service.TokenEncryptionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token Encryption Service Implementation.
 * Provides AES-256-GCM encryption for secure token storage.
 *
 * <p>Features:
 * <ul>
 *   <li>AES-256-GCM authenticated encryption</li>
 *   <li>Random 12-byte IV for each encryption (NIST recommended)</li>
 *   <li>128-bit authentication tag</li>
 *   <li>IV prepended to ciphertext for storage</li>
 * </ul>
 *
 * <p>Output format: Base64(IV[12 bytes] || Ciphertext || Tag[16 bytes])
 */
@Slf4j
@Service
public class TokenEncryptionServiceImpl implements TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits - NIST recommended
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    private static final String AES_ALGORITHM = "AES";

    private final TokenEncryptionProperties properties;
    private final SecureRandom secureRandom;
    private byte[] encryptionKey;
    private boolean configured = false;

    public TokenEncryptionServiceImpl(TokenEncryptionProperties properties) {
        this.properties = properties;
        this.secureRandom = new SecureRandom();
    }

    @PostConstruct
    public void init() {
        try {
            this.encryptionKey = properties.parseKey();
            this.configured = true;
            log.info("Token encryption service initialized successfully with AES-256-GCM");
        } catch (Exception e) {
            log.warn("Token encryption service not configured: {}. Token encryption disabled.", e.getMessage());
            this.configured = false;
        }
    }

    @Override
    public String encrypt(String plainText) {
        if (!configured) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR,
                "Encryption service is not configured. Set DEVICE_ENCRYPTION_KEY environment variable.");
        }
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // Encrypt
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] cipherTextWithIv = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, cipherTextWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, cipherTextWithIv, GCM_IV_LENGTH, cipherText.length);

            // Return as Base64
            return Base64.getEncoder().encodeToString(cipherTextWithIv);
        } catch (Exception e) {
            log.error("Failed to encrypt token: {}", e.getMessage());
            throw new SystemException(ErrorCode.SYSTEM_ERROR, "Token encryption failed", e);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        if (!configured) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR,
                "Encryption service is not configured. Set DEVICE_ENCRYPTION_KEY environment variable.");
        }
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Decode Base64
            byte[] cipherTextWithIv = Base64.getDecoder().decode(encryptedText);

            // Validate minimum length (IV + at least 1 block + tag)
            if (cipherTextWithIv.length < GCM_IV_LENGTH + 16) {
                throw new SystemException(ErrorCode.SYSTEM_ERROR,
                    "Invalid encrypted data: too short");
            }

            // Extract IV and ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[cipherTextWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(cipherTextWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherTextWithIv, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            // Initialize cipher
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Decrypt
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (SystemException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to decrypt token: {}", e.getMessage());
            throw new SystemException(ErrorCode.SYSTEM_ERROR, "Token decryption failed", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }
}