package com.everyarrow.dittoe.utility.retry;

import java.util.concurrent.Callable;

public interface Retry {
    int DEFAULT_MAX_ATTEMPTS = 3;

    <T> T execute(Callable<T> task) throws Exception;
}
