/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;

import java.util.Base64;

/**
 * AWS KMS encryption/decryption helper.
 * 
 * Supports ENC[...] string values (inline encrypted).
 * 
 * Credentials (in order of preference):
 * 1. AWS_PROFILE env var → uses ~/.aws/credentials profile (local dev)
 * 2. Default → EC2 instance role, env vars, container credentials, etc.
 */
public final class KmsCryptoHelper {
    static final String KMS_REGION_PROPERTY = "kms.region";
    static final String AWS_REGION_PROPERTY = "aws.region";
    static final String KMS_REGION_ENV = "KMS_REGION";
    static final String AWS_REGION_ENV = "AWS_REGION";
    static final String AWS_DEFAULT_REGION_ENV = "AWS_DEFAULT_REGION";
    
    /**
     * Build KMS client with appropriate credentials.
     * Default: Use DefaultCredentialsProvider (works on EC2, ECS, Lambda, etc.)
     * Override: Set AWS_PROFILE for local development with named profiles.
     */
    private static KmsClient buildKmsClient() {
        return KmsClient.builder()
                .region(resolveRegion())
                .credentialsProvider(getCredentialsProvider())
                .build();
    }

    static Region resolveRegion() {
        String region = firstNonBlank(
                System.getProperty(KMS_REGION_PROPERTY),
                System.getenv(KMS_REGION_ENV),
                System.getProperty(AWS_REGION_PROPERTY),
                System.getenv(AWS_REGION_ENV),
                System.getenv(AWS_DEFAULT_REGION_ENV));

        return region == null ? Region.US_EAST_1 : Region.of(region);
    }
    
    /**
     * Get credentials provider.
     * - If AWS_PROFILE is set: use that profile (for local dev)
     * - Otherwise: use default chain (EC2 role, env vars, etc.)
     */
    private static AwsCredentialsProvider getCredentialsProvider() {
        String profile = firstNonBlank(
                System.getenv("AWS_PROFILE"),
                System.getProperty("AWS_PROFILE"),
                System.getProperty("aws.profile"));
        
        if (profile != null && !profile.trim().isEmpty()) {
            System.out.println("🔐 KMS: Using profile '" + profile + "'");
            return ProfileCredentialsProvider.create(profile);
        }
        
        // Default: automatic credentials (EC2 instance role, ECS task role, etc.)
        System.out.println("🔐 KMS: Using default credentials (instance role/env vars)");
        return DefaultCredentialsProvider.create();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
    
    /**
     * Create a KMS client with a specific profile (for CLI tools, testing, etc.)
     */
    public static KmsClient withProfile(String profileName) {
        return withProfile(profileName, resolveRegion());
    }

    /**
     * Create a KMS client with a specific profile and region (for CLI tools, testing, etc.)
     */
    public static KmsClient withProfile(String profileName, Region region) {
        return KmsClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();
    }

    private KmsCryptoHelper() {}

    /**
     * Check if a value is already encrypted.
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith("ENC[") && value.endsWith("]");
    }
    
    /**
     * Encrypt a plaintext string using KMS.
     * Returns: ENC[base64-encoded-ciphertext]
     * 
     * SAFE: Skips already-encrypted values to prevent double-encryption.
     */
    public static String encrypt(String kmsKeyId, String plaintext) {
        // Safety check: don't double-encrypt
        if (isEncrypted(plaintext)) {
            System.out.println("⚠️  Skipping already-encrypted value");
            return plaintext;
        }
        
        EncryptRequest req = EncryptRequest.builder()
                .keyId(kmsKeyId)
                .plaintext(SdkBytes.fromUtf8String(plaintext))
                .build();

        byte[] encrypted = kms().encrypt(req).ciphertextBlob().asByteArray();

        return "ENC[" + Base64.getEncoder().encodeToString(encrypted) + "]";
    }

    /**
     * Decrypt an ENC[...] value.
     * If not ENC-prefixed, returns as-is.
     */
    public static String decrypt(String encryptedValue) {
        if (!encryptedValue.startsWith("ENC[")) {
            return encryptedValue;
        }

        String base64 = encryptedValue.substring(4, encryptedValue.length() - 1);
        byte[] ciphertext = Base64.getDecoder().decode(base64);

        DecryptRequest req = DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
                .build();
        
        return kms().decrypt(req).plaintext().asUtf8String();
    }

    private static KmsClient kms() {
        return DefaultKmsClientHolder.KMS;
    }

    private static final class DefaultKmsClientHolder {
        private static final KmsClient KMS = buildKmsClient();
    }
}
