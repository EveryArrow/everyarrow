package com.everyarrow.eventengine.core.adapters.external.http;

import java.time.Clock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class HttpRequestRateLimiter {
    static final long WINDOW_MILLIS = 60_000L;

    private final int maximumRequests;
    private final int maximumClients;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    HttpRequestRateLimiter(int maximumRequests, int maximumClients) {
        this(maximumRequests, maximumClients, Clock.systemUTC());
    }

    HttpRequestRateLimiter(int maximumRequests, int maximumClients, Clock clock) {
        if (maximumRequests <= 0) {
            throw new IllegalArgumentException("Maximum HTTP requests must be positive");
        }
        if (maximumClients <= 0) {
            throw new IllegalArgumentException("Maximum tracked HTTP clients must be positive");
        }
        this.maximumRequests = maximumRequests;
        this.maximumClients = maximumClients;
        this.clock = clock;
    }

    synchronized Decision check(String remoteAddress) {
        long now = clock.millis();
        String client = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress;
        Window window = windows.get(client);
        if (window == null) {
            if (windows.size() >= maximumClients) {
                removeExpired(now);
            }
            if (windows.size() >= maximumClients) {
                return new Decision(false, 1);
            }
            windows.put(client, new Window(now, 1));
            return new Decision(true, 0);
        }

        if (now - window.startedAtMillis >= WINDOW_MILLIS) {
            window.startedAtMillis = now;
            window.requests = 1;
            return new Decision(true, 0);
        }
        if (window.requests >= maximumRequests) {
            long remainingMillis = WINDOW_MILLIS - (now - window.startedAtMillis);
            return new Decision(false, Math.max(1, (remainingMillis + 999) / 1000));
        }
        window.requests++;
        return new Decision(true, 0);
    }

    private void removeExpired(long now) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().startedAtMillis >= WINDOW_MILLIS) {
                iterator.remove();
            }
        }
    }

    record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Window {
        private long startedAtMillis;
        private int requests;

        private Window(long startedAtMillis, int requests) {
            this.startedAtMillis = startedAtMillis;
            this.requests = requests;
        }
    }
}
