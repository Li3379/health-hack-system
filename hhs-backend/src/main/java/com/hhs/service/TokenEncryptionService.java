package com.hhs.service;

/**
 * Token Encryption Service
 * Provides AES-256-GCM encryption for secure token storage.
 * Used to encrypt OAuth tokens and other sensitive data.
 */
public interface TokenEncryptionService {

    /**
     * Encrypt a plain text string.
     * Uses AES-256-GCM with a random IV for each encryption.
     * The IV is prepended to the ciphertext and returned as Base64.
     *
     * @param plainText The text to encrypt
     * @return Base64-encoded string containing IV + ciphertext
     * @throws com.hhs.exception.SystemException if encryption fails
     */
    String encrypt(String plainText);

    /**
     * Decrypt an encrypted string.
     * Extracts the IV from the beginning of the encrypted data.
     *
     * @param encryptedText Base64-encoded string containing IV + ciphertext
     * @return The decrypted plain text
     * @throws com.hhs.exception.SystemException if decryption fails or data is corrupted
     */
    String decrypt(String encryptedText);

    /**
     * Check if the encryption service is properly configured.
     *
     * @return true if encryption key is configured and valid
     */
    boolean isConfigured();
}