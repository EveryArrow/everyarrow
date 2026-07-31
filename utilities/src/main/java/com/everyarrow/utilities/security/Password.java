/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.security;

import java.security.SecureRandom;
import java.util.Random;

public class Password {

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&*!?";

    private static final Random RNG = new SecureRandom();

    public static char[] generateRandomPassword(int length) {
        if (length < 8) throw new IllegalArgumentException("Password length must be ≥ 8");
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_CHARS.charAt(RNG.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString().toCharArray();
    }
}
