package com.everyarrow.dittoe.utility.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExponentialBackOffTest {

    @Test
    void executeCallableStillSucceedsAfterRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Retry retry = new ExponentialBackOff(3, 0);

        String result = retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void exhaustedRetriesThrowFinalFailure() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        AtomicInteger calls = new AtomicInteger();
        Retry retry = new ExponentialBackOff(2, 0);

        Exception thrown = assertThrows(Exception.class, () -> retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw first;
            }
            throw second;
        }));

        assertSame(second, thrown);
        assertEquals(2, calls.get());
    }

    @Test
    void retryScheduledIncrementsPipelineStats() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PipelineExecutionStats stats = new PipelineExecutionStats();
        ExponentialBackOff retry = new ExponentialBackOff(3, 0);
        retry.setStats(stats);

        String result = retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, stats.getRetriesAttempted());
    }
}
