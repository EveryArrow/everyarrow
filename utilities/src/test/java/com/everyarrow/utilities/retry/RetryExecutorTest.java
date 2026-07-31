/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryExecutorTest {

    @Test
    void succeedsOnFirstAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RetryExecutor executor = new RetryExecutor(RetryPolicy.defaultPolicy(), delay -> fail("sleep should not occur"));

        String result = executor.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, calls.get());
    }

    @Test
    void succeedsAfterRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Duration> slept = new ArrayList<>();
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(3)
                .backoffSchedule(BackoffSchedule.fixed(Duration.ofMillis(7)))
                .build();

        String result = new RetryExecutor(policy, slept::add).execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, calls.get());
        assertEquals(List.of(Duration.ofMillis(7)), slept);
    }

    @Test
    void exhaustionRethrowsFinalException() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(2)
                .backoffSchedule(BackoffSchedule.noDelay())
                .build();

        Exception thrown = assertThrows(Exception.class, () -> new RetryExecutor(policy).execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw first;
            }
            throw second;
        }));

        assertSame(second, thrown);
        assertEquals(2, calls.get());
    }

    @Test
    void classifierPreventsRetry() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException failure = new RuntimeException("permanent");
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(3)
                .retryClassifier(RetryClassifier.neverRetry())
                .build();

        Exception thrown = assertThrows(Exception.class, () -> new RetryExecutor(policy, delay -> fail("sleep should not occur"))
                .execute(() -> {
                    calls.incrementAndGet();
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, calls.get());
    }

    @Test
    void backoffSchedulesReturnExpectedDelays() {
        assertEquals(Duration.ZERO, BackoffSchedule.noDelay().delayFor(1));
        assertEquals(Duration.ofMillis(25), BackoffSchedule.fixed(Duration.ofMillis(25)).delayFor(3));

        BackoffSchedule linear = BackoffSchedule.linear(Duration.ofMillis(10), Duration.ofMillis(5));
        assertEquals(Duration.ofMillis(10), linear.delayFor(1));
        assertEquals(Duration.ofMillis(15), linear.delayFor(2));
        assertEquals(Duration.ofMillis(20), linear.delayFor(3));

        BackoffSchedule exponential = BackoffSchedule.exponential(Duration.ofMillis(10));
        assertEquals(Duration.ofMillis(10), exponential.delayFor(1));
        assertEquals(Duration.ofMillis(20), exponential.delayFor(2));
        assertEquals(Duration.ofMillis(40), exponential.delayFor(3));
    }

    @Test
    void observerReceivesFailureRetryAndSuccessEvents() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(2)
                .retryObserver(new EventRecordingRetryObserver(events))
                .build();

        new RetryExecutor(policy).execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary");
            }
            return "done";
        });

        assertEquals(List.of("failure:1", "retry:1", "success:2"), events);
    }

    @Test
    void observerReceivesExhaustionEvent() {
        List<String> events = new ArrayList<>();
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(2)
                .retryObserver(new EventRecordingRetryObserver(events))
                .build();

        assertThrows(Exception.class, () -> new RetryExecutor(policy).execute(() -> {
            throw new IllegalStateException("still failing");
        }));

        assertEquals(List.of("failure:1", "retry:1", "failure:2", "exhausted:2"), events);
    }

    @Test
    void interruptedSleepRestoresInterruptStatus() {
        Thread.interrupted();
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(2)
                .backoffSchedule(BackoffSchedule.fixed(Duration.ofMillis(1)))
                .build();

        assertThrows(InterruptedException.class, () -> new RetryExecutor(policy, delay -> {
            throw new InterruptedException("stop");
        }).execute(() -> {
            throw new IllegalStateException("temporary");
        }));

        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @SuppressWarnings("deprecation")
    @Test
    void deprecatedStrategiesRethrowInsteadOfReturningNull() {
        RuntimeException fixedFailure = new RuntimeException("fixed");
        RuntimeException linearFailure = new RuntimeException("linear");
        RuntimeException exponentialFailure = new RuntimeException("exponential");

        Exception fixedThrown = assertThrows(Exception.class, () ->
                new FixedIntervalRetryStrategy(0, 2, null).retry(() -> {
                    throw fixedFailure;
                }));
        Exception linearThrown = assertThrows(Exception.class, () ->
                new LinearBackoffRetryStrategy(0, 0, 2, null).retry(() -> {
                    throw linearFailure;
                }));
        Exception exponentialThrown = assertThrows(Exception.class, () ->
                new ExponentialBackoffRetryStrategy(0, 2, null).retry(() -> {
                    throw exponentialFailure;
                }));

        assertSame(fixedFailure, fixedThrown);
        assertSame(linearFailure, linearThrown);
        assertSame(exponentialFailure, exponentialThrown);
    }

    private static final class EventRecordingRetryObserver implements RetryObserver {
        private final List<String> events;

        private EventRecordingRetryObserver(List<String> events) {
            this.events = events;
        }

        @Override
        public void onFailure(RetryAttempt attempt) {
            events.add("failure:" + attempt.getAttemptNumber());
        }

        @Override
        public void onRetryScheduled(RetryAttempt attempt) {
            events.add("retry:" + attempt.getAttemptNumber());
        }

        @Override
        public void onSuccess(RetryAttempt attempt) {
            events.add("success:" + attempt.getAttemptNumber());
        }

        @Override
        public void onExhausted(RetryAttempt attempt) {
            events.add("exhausted:" + attempt.getAttemptNumber());
        }
    }
}
