package com.everyarrow.dittoe.utility.retry;


import java.util.Locale;

public enum RetryType {

    EXPONENTIAL_BACKOFF(ExponentialBackOff.class);

    private final Class<? extends Retry> clazz;

    RetryType(Class<? extends Retry> clazz) {
        this.clazz = clazz;
    }

    public static RetryType getDefault() {
        return EXPONENTIAL_BACKOFF;
    }

    public static RetryType getStrategy(String retryType) {
        retryType = retryType.trim().toUpperCase(Locale.ROOT);
        return RetryType.valueOf(retryType);
    }

    public Class<? extends Retry> classReference() {
        return clazz;
    }
}
