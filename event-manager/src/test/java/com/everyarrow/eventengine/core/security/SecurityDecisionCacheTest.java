/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.security;

import com.everyarrow.eventengine.core.hooks.SecurityAuthorizationResolver;
import com.everyarrow.eventengine.core.hooks.SecurityPrincipalResolver;
import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import com.everyarrow.utilities.cache.BoundedTtlCache;
import com.everyarrow.utilities.cache.CachePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityDecisionCacheTest {

    @Test
    void cachesResolvedPrincipalOnly() {
        SecurityDecisionCache cache = cache();
        AtomicInteger calls = new AtomicInteger();

        SecurityPrincipalResolver.ResolvedPrincipal first = cache.resolvePrincipal("principal:key", () -> {
            calls.incrementAndGet();
            return new SecurityPrincipalResolver.ResolvedPrincipal("principal-1", "customer-1");
        });
        SecurityPrincipalResolver.ResolvedPrincipal second = cache.resolvePrincipal("principal:key", () -> {
            calls.incrementAndGet();
            return new SecurityPrincipalResolver.ResolvedPrincipal("principal-2", "customer-2");
        });

        assertEquals("principal-1", first.principalId());
        assertEquals("principal-1", second.principalId());
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotCacheUnresolvedPrincipal() {
        SecurityDecisionCache cache = cache();
        AtomicInteger calls = new AtomicInteger();

        cache.resolvePrincipal("principal:key", () -> {
            calls.incrementAndGet();
            return new SecurityPrincipalResolver.ResolvedPrincipal(null, null);
        });
        cache.resolvePrincipal("principal:key", () -> {
            calls.incrementAndGet();
            return new SecurityPrincipalResolver.ResolvedPrincipal("principal-1", "customer-1");
        });

        assertEquals(2, calls.get());
    }

    @Test
    void cachesAuthorizedDecisionOnly() {
        SecurityDecisionCache cache = cache();
        AtomicInteger calls = new AtomicInteger();

        SecurityAuthorizationResolver.AuthorizationResult first = cache.authorize("auth:key", () -> {
            calls.incrementAndGet();
            return new SecurityAuthorizationResolver.AuthorizationResult(
                    true,
                    true,
                    "principal-1",
                    "customer-1",
                    "app-1",
                    false);
        });
        SecurityAuthorizationResolver.AuthorizationResult second = cache.authorize("auth:key", () -> {
            calls.incrementAndGet();
            return new SecurityAuthorizationResolver.AuthorizationResult(
                    false,
                    false,
                    null,
                    null,
                    null,
                    false);
        });

        assertEquals(true, first.authorized());
        assertEquals(true, second.authorized());
        assertEquals(1, calls.get());
    }

    @Test
    void clearForcesReload() {
        SecurityDecisionCache cache = cache();
        AtomicInteger calls = new AtomicInteger();

        cache.authorize("auth:key", () -> authorized(calls.incrementAndGet()));
        cache.clear();
        SecurityAuthorizationResolver.AuthorizationResult result =
                cache.authorize("auth:key", () -> authorized(calls.incrementAndGet()));

        assertEquals("principal-2", result.principalId());
        assertEquals(2, calls.get());
    }

    private SecurityAuthorizationResolver.AuthorizationResult authorized(int number) {
        return new SecurityAuthorizationResolver.AuthorizationResult(
                true,
                true,
                "principal-" + number,
                "customer-1",
                "app-1",
                false);
    }

    private SecurityDecisionCache cache() {
        return new SecurityDecisionCache(
                true,
                new BoundedTtlCache<>(CachePolicy.of(10, Duration.ofSeconds(1))),
                new BoundedTtlCache<>(CachePolicy.of(10, Duration.ofSeconds(1))));
    }
}
