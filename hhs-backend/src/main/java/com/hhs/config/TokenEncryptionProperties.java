package com.hhs.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for device token encryption.
 * Uses AES-256-GCM for secure token storage.
 */
@Component
@ConfigurationProperties(prefix = "device.encryption")
@Data
public class TokenEncryptionProperties {

    /**
     * AES-256 encryption key (256-bit = 32 bytes).
     * Must be provided via DEVICE_ENCRYPTION_KEY environment variable.
     * Generate with: openssl rand -base64 32
     */
    private String key;

    @PostConstruct
    public void validate() {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "DEVICE_ENCRYPTION_KEY is required but not set.\n" +
                "Expected: 256-bit (32-byte) random key encoded in Base64 or hexadecimal.\n" +
                "Fix: Set environment variable before starting the application.\n" +
                "Example: export DEVICE_ENCRYPTION_KEY=\"your-256-bit-encryption-key-here\""
            );
        }
        // Validate key length (256-bit = 32 bytes)
        byte[] keyBytes = parseKey();
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "DEVICE_ENCRYPTION_KEY must be 256 bits (32 bytes). " +
                "Current length: " + keyBytes.length + " bytes. " +
                "Generate a valid key with: openssl rand -base64 32"
            );
        }
    }

    /**
     * Parse the encryption key to byte array.
     * Supports both Base64 and hexadecimal formats.
     *
     * @return 32-byte key array
     */
    public byte[] parseKey() {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Encryption key is not configured");
        }
        try {
            // Try Base64 first
            return java.util.Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException e) {
            // Try hexadecimal if Base64 fails
            String hex = key.trim();
            if (hex.length() == 64) {
                byte[] result = new byte[32];
                for (int i = 0; i < 32; i++) {
                    result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                return result;
            }
            // Use raw string bytes as fallback (must be exactly 32 characters)
            byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length == 32) {
                return bytes;
            }
            throw new IllegalStateException(
                "Invalid encryption key format. Use Base64, hexadecimal (64 chars), or raw 32-byte string."
            );
        }
    }
}