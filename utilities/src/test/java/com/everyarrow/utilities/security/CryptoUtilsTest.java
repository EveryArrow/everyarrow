/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoUtilsTest {

    @Test
    void allowsApprovedHashAlgorithms() {
        assertDoesNotThrow(() -> CryptoUtils.generateHash("SHA-256", "data"));
        assertDoesNotThrow(() -> CryptoUtils.generateHash("SHA-384", "data"));
        assertDoesNotThrow(() -> CryptoUtils.generateHash("SHA-512", "data"));
    }

    @Test
    void rejectsWeakHashAlgorithms() {
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.generateHash("MD5", "data"));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtils.generateHash("SHA-1", "data"));
    }
}
