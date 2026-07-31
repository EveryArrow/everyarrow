/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.Ec2ClientBuilder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AwsClientBuilderConfigurerTest {

    @Test
    void trimsAndResolvesExplicitProfileAndRegion() {
        assertInstanceOf(
                ProfileCredentialsProvider.class,
                AwsClientBuilderConfigurer.credentialsProvider(" EveryArrow "));
        assertEquals(Region.US_EAST_1, AwsClientBuilderConfigurer.region(" us-east-1 "));
    }

    @Test
    void leavesBlankValuesToTheDefaultAwsProviderChain() {
        assertNull(AwsClientBuilderConfigurer.credentialsProvider("  "));
        assertNull(AwsClientBuilderConfigurer.region(null));
    }

    @Test
    void configuresAnyAwsBuilderWithoutOwningResourcePolicy() {
        Ec2ClientBuilder builder = Ec2Client.builder();

        Ec2ClientBuilder configured = assertDoesNotThrow(
                () -> AwsClientBuilderConfigurer.configure(builder, "EveryArrow", "us-east-1"));

        assertSame(builder, configured);
    }
}
