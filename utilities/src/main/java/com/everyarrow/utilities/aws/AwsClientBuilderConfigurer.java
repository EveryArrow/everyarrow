/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.regions.Region;

/**
 * Applies shared profile and region selection to any AWS SDK client builder.
 * Resource authorization and lifecycle policy remain the caller's responsibility.
 */
public final class AwsClientBuilderConfigurer {

    private AwsClientBuilderConfigurer() {
    }

    public static <B extends AwsClientBuilder<B, C>, C extends SdkClient> B configure(
            B builder,
            String profile,
            String region) {
        if (builder == null) {
            throw new IllegalArgumentException("AWS client builder is required");
        }
        AwsCredentialsProvider credentialsProvider = credentialsProvider(profile);
        if (credentialsProvider != null) {
            builder.credentialsProvider(credentialsProvider);
        }
        Region resolvedRegion = region(region);
        if (resolvedRegion != null) {
            builder.region(resolvedRegion);
        }
        return builder;
    }

    static AwsCredentialsProvider credentialsProvider(String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        return ProfileCredentialsProvider.create(profile.trim());
    }

    static Region region(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Region.of(value.trim());
    }
}
