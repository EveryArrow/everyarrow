package com.everyarrow.dittoe.utility.retry;

import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.utilities.retry.BackoffSchedule;
import com.everyarrow.utilities.retry.RetryAttempt;
import com.everyarrow.utilities.retry.RetryExecutor;
import com.everyarrow.utilities.retry.RetryObserver;
import com.everyarrow.utilities.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;

public class ExponentialBackOff implements Retry {
    private static final long DEFAULT_DELAY_MS = 1000; // 1 second
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final int maxAttempts;
    private final long delay;
    private PipelineExecutionStats stats;

    public ExponentialBackOff() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_DELAY_MS);
    }

    public ExponentialBackOff(int maxAttempts, long delay) {
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    @Override
    public <T> T execute(Callable<T> task) throws Exception {
        return new RetryExecutor(createPolicy()).execute(task::call);
    }

    public void setStats(PipelineExecutionStats stats) {
        this.stats = stats;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getDelay() {
        return delay;
    }

    public Logger getLogger() {
        return logger;
    }

    private RetryPolicy createPolicy() {
        return RetryPolicy.builder()
                .name("dittoe-exponential-backoff")
                .maxAttempts(maxAttempts)
                .backoffSchedule(BackoffSchedule.exponential(Duration.ofMillis(delay)))
                .retryObserver(new DittoeRetryObserver())
                .build();
    }

    private final class DittoeRetryObserver implements RetryObserver {
        @Override
        public void onRetryScheduled(RetryAttempt attempt) {
            if (stats != null) {
                stats.incrementRetriesAttempted();
            }
            getLogger().warn(
                    "Retrying attempt {} of {} after failure: {}: {}",
                    attempt.getAttemptNumber() + 1,
                    attempt.getMaxAttempts(),
                    attempt.getLastFailure().getClass().getSimpleName(),
                    attempt.getLastFailure().getMessage());
        }

        @Override
        public void onExhausted(RetryAttempt attempt) {
            getLogger().error(
                    "Retry exhausted after {} attempts: {}: {}",
                    attempt.getMaxAttempts(),
                    attempt.getLastFailure().getClass().getSimpleName(),
                    attempt.getLastFailure().getMessage());
        }
    }
}
