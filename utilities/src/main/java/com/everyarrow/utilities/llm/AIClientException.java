/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.llm;

/**
 * Exception class for errors during AI client operations.
 */
public class AIClientException extends Exception {
    /**
     * Constructs a new AIClientException with the specified detail message.
     *
     * @param message The detail message.
     */
    public AIClientException(String message) {
        super(message);
    }

    /**
     * Constructs a new AIClientException with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause   The cause.
     */
    public AIClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
