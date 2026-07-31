/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.environment;

import com.everyarrow.utilities.aws.KmsCryptoHelper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class EnvironmentDecryptor {

    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    private EnvironmentDecryptor() {}

    public static String resolve(String value) {
        if (value == null) return null;

        if (!value.startsWith("ENC[")) {
            return value;
        }
        try {
            return cache.computeIfAbsent(value, KmsCryptoHelper::decrypt);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException(
                    "ENC[...] environment values require an operator-provided decryption runtime. "
                            + "The public Event Manager distribution does not include AWS SDK classes.",
                    e);
        }
    }
}
