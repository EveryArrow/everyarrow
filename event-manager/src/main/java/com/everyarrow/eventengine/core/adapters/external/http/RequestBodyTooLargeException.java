package com.everyarrow.eventengine.core.adapters.external.http;

import java.io.IOException;

public final class RequestBodyTooLargeException extends IOException {

    private final int limitBytes;

    public RequestBodyTooLargeException(int limitBytes) {
        super("HTTP request body exceeds the configured limit of " + limitBytes + " bytes");
        this.limitBytes = limitBytes;
    }

    public int getLimitBytes() {
        return limitBytes;
    }
}
