package com.everyarrow.eventengine.core.adapters.external.http;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestRateLimiterTest {

    @Test
    void limitsEachSocketPeerWithinOneMinute() {
        MutableClock clock = new MutableClock();
        HttpRequestRateLimiter limiter = new HttpRequestRateLimiter(2, 10, clock);

        assertTrue(limiter.check("192.0.2.1").allowed());
        assertTrue(limiter.check("192.0.2.1").allowed());
        assertFalse(limiter.check("192.0.2.1").allowed());
        assertTrue(limiter.check("192.0.2.2").allowed());

        clock.advanceMillis(HttpRequestRateLimiter.WINDOW_MILLIS);
        assertTrue(limiter.check("192.0.2.1").allowed());
    }

    @Test
    void boundsTrackedClientStateAndReclaimsExpiredEntries() {
        MutableClock clock = new MutableClock();
        HttpRequestRateLimiter limiter = new HttpRequestRateLimiter(1, 1, clock);

        assertTrue(limiter.check("192.0.2.1").allowed());
        assertFalse(limiter.check("192.0.2.2").allowed());

        clock.advanceMillis(HttpRequestRateLimiter.WINDOW_MILLIS);
        assertTrue(limiter.check("192.0.2.2").allowed());
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        void advanceMillis(long milliseconds) {
            instant = instant.plusMillis(milliseconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
