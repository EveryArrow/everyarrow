/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public class S3BucketManager {
    
    private final S3Client s3Client;
    private final Region region;
    
    public S3BucketManager(Region region) {
        this.region = region;
        this.s3Client = S3Client.builder()
                .region(region)
                .build();
    }
    
    /**
     * Creates an S3 bucket with the specified name
     */
    public String createBucket(String bucketName) {
        try {
            CreateBucketRequest.Builder requestBuilder = CreateBucketRequest.builder()
                    .bucket(bucketName);
            
            // For regions other than us-east-1, we need to specify the location constraint
            if (!region.equals(Region.US_EAST_1)) {
                requestBuilder.createBucketConfiguration(
                    CreateBucketConfiguration.builder()
                        .locationConstraint(BucketLocationConstraint.fromValue(region.id()))
                        .build()
                );
            }
            
            CreateBucketResponse response = s3Client.createBucket(requestBuilder.build());
            System.out.println("[S3] Bucket created: " + bucketName);
            return response.location();
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            System.out.println("[S3] Bucket already exists: " + bucketName);
            return bucketName;
        } catch (S3Exception e) {
            System.err.println("[S3] Error creating bucket: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }

    public boolean bucketExists(String bucketName) {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(request);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            // For permissions or other errors, rethrow so the caller can see the problem
            throw e;
        }
    }
    
    /**
     * Enables versioning on the bucket
     */
    public void enableVersioning(String bucketName) {
        try {
            VersioningConfiguration versioningConfig = VersioningConfiguration.builder()
                    .status(BucketVersioningStatus.ENABLED)
                    .build();
            
            PutBucketVersioningRequest request = PutBucketVersioningRequest.builder()
                    .bucket(bucketName)
                    .versioningConfiguration(versioningConfig)
                    .build();
            
            s3Client.putBucketVersioning(request);
            System.out.println("[S3] Versioning enabled for: " + bucketName);
        } catch (S3Exception e) {
            System.err.println("[S3] Error enabling versioning: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Configures the bucket for static website hosting
     */
    public void enableStaticWebsiteHosting(String bucketName, String indexDocument, String errorDocument) {
        try {
            WebsiteConfiguration websiteConfig = WebsiteConfiguration.builder()
                    .indexDocument(IndexDocument.builder().suffix(indexDocument).build())
                    .errorDocument(ErrorDocument.builder().key(errorDocument).build())
                    .build();
            
            PutBucketWebsiteRequest request = PutBucketWebsiteRequest.builder()
                    .bucket(bucketName)
                    .websiteConfiguration(websiteConfig)
                    .build();
            
            s3Client.putBucketWebsite(request);
            System.out.println("[S3] Static website hosting enabled for: " + bucketName);
        } catch (S3Exception e) {
            System.err.println("[S3] Error enabling website hosting: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Sets bucket policy to allow public read access (for static website hosting)
     */
    public void setPublicReadPolicy(String bucketName) {
        String policy = String.format("""
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "PublicReadGetObject",
                        "Effect": "Allow",
                        "Principal": "*",
                        "Action": "s3:GetObject",
                        "Resource": "arn:aws:s3:::%s/*"
                    }
                ]
            }
            """, bucketName);
        
        try {
            PutBucketPolicyRequest request = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policy)
                    .build();
            
            s3Client.putBucketPolicy(request);
            System.out.println("[S3] Public read policy set for: " + bucketName);
        } catch (S3Exception e) {
            System.err.println("[S3] Error setting bucket policy: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    public void setCloudFrontAccessPolicy(String bucketName, String distributionId, String awsAccountId) {
        String distributionArn = String.format("arn:aws:cloudfront::%s:distribution/%s", awsAccountId, distributionId);
        String policy = String.format("""
            {
                "Version": "2008-10-17",
                "Id": "PolicyForCloudFrontPrivateContent",
                "Statement": [
                    {
                        "Sid": "AllowCloudFrontServicePrincipal",
                        "Effect": "Allow",
                        "Principal": {
                            "Service": "cloudfront.amazonaws.com"
                        },
                        "Action": "s3:GetObject",
                        "Resource": "arn:aws:s3:::%s/*",
                        "Condition": {
                            "StringEquals": {
                                "AWS:SourceArn": "%s"
                            }
                        }
                    }
                ]
            }
            """, bucketName, distributionArn);
        
        try {
            PutBucketPolicyRequest request = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policy)
                    .build();
            
            s3Client.putBucketPolicy(request);
            System.out.println("[S3] CloudFront access policy set for: " + bucketName);
        } catch (S3Exception e) {
            System.err.println("[S3] Error setting CloudFront access policy: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    /**
     * Enables server-side encryption for the bucket
     */
    public void enableEncryption(String bucketName) {
        try {
            ServerSideEncryptionByDefault encryptionByDefault = ServerSideEncryptionByDefault.builder()
                    .sseAlgorithm(ServerSideEncryption.AES256)
                    .build();
            
            ServerSideEncryptionRule rule = ServerSideEncryptionRule.builder()
                    .applyServerSideEncryptionByDefault(encryptionByDefault)
                    .build();
            
            ServerSideEncryptionConfiguration encryptionConfig = ServerSideEncryptionConfiguration.builder()
                    .rules(rule)
                    .build();
            
            PutBucketEncryptionRequest request = PutBucketEncryptionRequest.builder()
                    .bucket(bucketName)
                    .serverSideEncryptionConfiguration(encryptionConfig)
                    .build();
            
            s3Client.putBucketEncryption(request);
            System.out.println("[S3] Encryption enabled for: " + bucketName);
        } catch (S3Exception e) {
            System.err.println("[S3] Error enabling encryption: " + e.awsErrorDetails().errorMessage());
            throw e;
        }
    }
    
    public void close() {
        s3Client.close();
    }
}
