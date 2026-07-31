/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.KeyListEntry;
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.ListKeysRequest;

public class KmsManager {

    private final KmsClient kmsClient;

    public KmsManager(Region region) {
        this.kmsClient = KmsClient.builder()
                .region(region)
                .build();
    }

    /**
     * Creates a new symmetric encryption KMS key with the given description.
     * Returns the key ARN.
     */
    public String createKey(String description) {
        try {
            ListKeysRequest listRequest = ListKeysRequest.builder().build();
            for (KeyListEntry entry : kmsClient.listKeys(listRequest).keys()) {
                DescribeKeyResponse existingResp = kmsClient.describeKey(
                        DescribeKeyRequest.builder().keyId(entry.keyId()).build());
                KeyMetadata existingMeta = existingResp.keyMetadata();
                if (description.equals(existingMeta.description())) {
                    System.out.println("[KMS] Key with matching description already exists, reusing: " + existingMeta.arn());
                    return existingMeta.arn();
                }
            }

            CreateKeyRequest request = CreateKeyRequest.builder()
                    .description(description)
                    .keyUsage(KeyUsageType.ENCRYPT_DECRYPT)
                    .build();

            CreateKeyResponse response = kmsClient.createKey(request);
            KeyMetadata meta = response.keyMetadata();

            System.out.println("[KMS] Created key: " + meta.keyId());
            System.out.println("[KMS] Arn: " + meta.arn());
            System.out.println("[KMS] Description: " + meta.description());

            return meta.arn();
        } catch (KmsException e) {
            System.err.println("[KMS] Error creating key: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    /**
     * Describes a KMS key and logs basic metadata. Returns the key ARN.
     */
    public String describeKey(String keyId) {
        try {
            DescribeKeyRequest request = DescribeKeyRequest.builder()
                    .keyId(keyId)
                    .build();

            DescribeKeyResponse response = kmsClient.describeKey(request);
            KeyMetadata meta = response.keyMetadata();

            System.out.println("[KMS] KeyId: " + meta.keyId());
            System.out.println("[KMS] Arn: " + meta.arn());
            System.out.println("[KMS] Description: " + meta.description());
            System.out.println("[KMS] Enabled: " + meta.enabled());
            System.out.println("[KMS] KeyUsage: " + meta.keyUsage());

            return meta.arn();
        } catch (KmsException e) {
            System.err.println("[KMS] Error describing key: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    public void close() {
        kmsClient.close();
    }
}
