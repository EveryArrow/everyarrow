package com.everyarrow.eventengine.core.hooks;

public class SecurityAccessDeniedException extends RuntimeException {

    public SecurityAccessDeniedException(String message) {
        super(message);
    }
}