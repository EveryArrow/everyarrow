/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.exceptions;

/**
 * Exception class for errors during template processing.
 */
public class TemplateProcessingException extends Exception {
    /**
     * Constructs a new TemplateProcessingException with the specified detail message.
     *
     * @param message The detail message.
     */
    public TemplateProcessingException(String message) {
        super(message);
    }

    /**
     * Constructs a new TemplateProcessingException with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause   The cause.
     */
    public TemplateProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
