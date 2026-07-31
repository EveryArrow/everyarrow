/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class providing cryptographic functionalities.
 */
public final class CryptoUtils {
    // Constants for the encryption algorithm, transformation, and key specifications.
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256; // Key size in bits for AES-256.
    private static final int TAG_LENGTH_BIT = 128; // Authentication tag length in bits for GCM.
    private static final int IV_LENGTH_BYTE = 12; // IV length in bytes for GCM (96 bits).
    private static final Set<String> APPROVED_HASH_ALGORITHMS = Set.of("SHA-256", "SHA-384", "SHA-512");

    // Private constructor to prevent instantiation of this utility class.
    private CryptoUtils() {
    }

    /**
     * Generates a new AES key.
     *
     * @return The generated AES key encoded in Base64.
     * @throws Exception If key generation fails.
     */
    public static String generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        SecretKey secretKey = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * Creates a secret key from a Base64 encoded string.
     *
     * @param encodedKey The Base64 encoded key.
     * @return The decoded SecretKey object.
     */
    public static SecretKey createKeyFromString(String encodedKey) {
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, ENCRYPTION_ALGORITHM);
    }

    /**
     * Encrypts a plain text string using AES/GCM/NoPadding.
     *
     * @param plainText The plain text to encrypt.
     * @param secretKey The AES key for encryption.
     * @return The encrypted text encoded in Base64.
     * @throws RuntimeException If encryption fails.
     */
    public static String encrypt(String plainText, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes());

            byte[] encryptedTextWithIv = new byte[IV_LENGTH_BYTE + cipherText.length];
            System.arraycopy(iv, 0, encryptedTextWithIv, 0, IV_LENGTH_BYTE);
            System.arraycopy(cipherText, 0, encryptedTextWithIv, IV_LENGTH_BYTE, cipherText.length);

            return Base64.getEncoder().encodeToString(encryptedTextWithIv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt.", e);
        }
    }

    /**
     * Decrypts an encrypted text string using AES/GCM/NoPadding.
     *
     * @param encryptedText The encrypted text encoded in Base64.
     * @param secretKey     The AES key for decryption.
     * @return The decrypted plain text.
     * @throws RuntimeException If decryption fails.
     */
    public static String decrypt(String encryptedText, SecretKey secretKey) {
        try {
            byte[] encryptedTextWithIv = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            System.arraycopy(encryptedTextWithIv, 0, iv, 0, IV_LENGTH_BYTE);

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

            byte[] cipherText = new byte[encryptedTextWithIv.length - IV_LENGTH_BYTE];
            System.arraycopy(encryptedTextWithIv, IV_LENGTH_BYTE, cipherText, 0, cipherText.length);

            byte[] decryptedText = cipher.doFinal(cipherText);

            return new String(decryptedText);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt.", e);
        }
    }

    /**
     * Generates a hash of a given string using an approved hashing algorithm.
     *
     * @param algorithm The name of the hashing algorithm: "SHA-256", "SHA-384", or "SHA-512".
     * @param data      The input data to hash.
     * @return A Base64 encoded hash of the input data.
     * @throws NoSuchAlgorithmException If hash algorithm is not found.
     * @throws IllegalArgumentException If the algorithm is weak or not approved for security-sensitive use.
     */
    public static String generateHash(String algorithm, String data) throws NoSuchAlgorithmException {
        String normalizedAlgorithm = normalizeHashAlgorithm(algorithm);
        if (!APPROVED_HASH_ALGORITHMS.contains(normalizedAlgorithm)) {
            throw new IllegalArgumentException("Unsupported hash algorithm for security use: " + algorithm);
        }
        MessageDigest digest = MessageDigest.getInstance(normalizedAlgorithm);
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static String normalizeHashAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Hash algorithm is required.");
        }
        return algorithm.trim().toUpperCase(Locale.ROOT);
    }
}
