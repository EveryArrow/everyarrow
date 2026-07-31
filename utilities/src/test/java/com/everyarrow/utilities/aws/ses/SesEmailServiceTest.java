/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.aws.ses;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SesEmailServiceTest {

    @Test
    void resolvesSesSpecificRegionBeforeGenericAwsRegion() {
        withSystemProperties("ses.region", "us-west-2", "aws.region", "eu-west-1", () ->
                assertEquals(Region.US_WEST_2, SesEmailService.resolveRegion()));
    }

    @Test
    void resolvesGenericAwsRegionWhenSesRegionIsNotSet() {
        withSystemProperties("ses.region", null, "aws.region", "eu-west-1", () ->
                assertEquals(Region.EU_WEST_1, SesEmailService.resolveRegion()));
    }

    private static void withSystemProperties(String firstKey, String firstValue,
                                             String secondKey, String secondValue,
                                             Runnable assertion) {
        String originalFirst = System.getProperty(firstKey);
        String originalSecond = System.getProperty(secondKey);
        try {
            setOrClear(firstKey, firstValue);
            setOrClear(secondKey, secondValue);
            assertion.run();
        } finally {
            setOrClear(firstKey, originalFirst);
            setOrClear(secondKey, originalSecond);
        }
    }

    private static void setOrClear(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
