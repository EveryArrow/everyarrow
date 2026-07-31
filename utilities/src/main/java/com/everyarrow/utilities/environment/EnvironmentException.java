/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.environment;

public final class EnvironmentException extends RuntimeException {
    public EnvironmentException(String message) { super(message); }
    public EnvironmentException(String message, Throwable cause) { super(message, cause); }
}
