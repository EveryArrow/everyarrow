/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.environment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void keysExposeNamesWithoutRequiringValueSnapshots() {
        Environment.put("SAMPLE.DB_HOST", "localhost");

        assertTrue(Environment.getInstance().keys().contains("SAMPLE.DB_HOST"));
    }
}
