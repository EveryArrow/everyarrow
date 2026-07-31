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
import com.everyarrow.utilities.cache.BoundedTtlCache;
import com.everyarrow.utilities.cache.CachePolicy;
import com.everyarrow.utilities.cache.CacheStats;
import com.everyarrow.utilities.environment.Environment;

import java.time.Duration;
import java.util.function.Supplier;

public final class SecurityDecisionCache {

    private final boolean enabled;
    private final BoundedTtlCache<String, SecurityPrincipalResolver.ResolvedPrincipal> principalCache;
    private final BoundedTtlCache<String, SecurityAuthorizationResolver.AuthorizationResult> authorizationCache;

    public SecurityDecisionCache(boolean enabled,
                                 BoundedTtlCache<String, SecurityPrincipalResolver.ResolvedPrincipal> principalCache,
                                 BoundedTtlCache<String, SecurityAuthorizationResolver.AuthorizationResult> authorizationCache) {
        this.enabled = enabled;
        this.principalCache = principalCache;
        this.authorizationCache = authorizationCache;
    }

    public static SecurityDecisionCache fromEnvironment() {
        Environment env = Environment.getInstance();
        boolean enabled = Boolean.parseBoolean(env.get("EA_SECURITY_CACHE_ENABLED", "true"));
        Duration ttl = Duration.ofMillis(longValue(env, "EA_SECURITY_CACHE_TTL_MS", 60_000));
        int principalMax = intValue(env, "EA_SECURITY_CACHE_PRINCIPAL_MAX_ENTRIES", 2_000);
        int authorizationMax = intValue(env, "EA_SECURITY_CACHE_AUTHORIZATION_MAX_ENTRIES", 10_000);

        return new SecurityDecisionCache(
                enabled,
                new BoundedTtlCache<>(CachePolicy.of(principalMax, ttl)),
                new BoundedTtlCache<>(CachePolicy.of(authorizationMax, ttl)));
    }

    public SecurityPrincipalResolver.ResolvedPrincipal resolvePrincipal(
            String cacheKey,
            Supplier<SecurityPrincipalResolver.ResolvedPrincipal> loader) {
        if (!enabled || isBlank(cacheKey)) {
            return loadPrincipal(loader);
        }

        SecurityPrincipalResolver.ResolvedPrincipal cached = principalCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        SecurityPrincipalResolver.ResolvedPrincipal loaded = loadPrincipal(loader);
        if (loaded != null && loaded.isResolved()) {
            principalCache.put(cacheKey, loaded);
        }
        return loaded;
    }

    public SecurityAuthorizationResolver.AuthorizationResult authorize(
            String cacheKey,
            Supplier<SecurityAuthorizationResolver.AuthorizationResult> loader) {
        if (!enabled || isBlank(cacheKey)) {
            return loadAuthorization(loader);
        }

        SecurityAuthorizationResolver.AuthorizationResult cached = authorizationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        SecurityAuthorizationResolver.AuthorizationResult loaded = loadAuthorization(loader);
        if (loaded != null && loaded.authorized()) {
            authorizationCache.put(cacheKey, loaded);
        }
        return loaded;
    }

    public void clear() {
        principalCache.clear();
        authorizationCache.clear();
    }

    public CacheStats principalStats() {
        return principalCache.stats();
    }

    public CacheStats authorizationStats() {
        return authorizationCache.stats();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private SecurityPrincipalResolver.ResolvedPrincipal loadPrincipal(
            Supplier<SecurityPrincipalResolver.ResolvedPrincipal> loader) {
        SecurityPrincipalResolver.ResolvedPrincipal loaded = loader == null ? null : loader.get();
        return loaded == null ? new SecurityPrincipalResolver.ResolvedPrincipal(null, null, null) : loaded;
    }

    private SecurityAuthorizationResolver.AuthorizationResult loadAuthorization(
            Supplier<SecurityAuthorizationResolver.AuthorizationResult> loader) {
        SecurityAuthorizationResolver.AuthorizationResult loaded = loader == null ? null : loader.get();
        return loaded == null
                ? new SecurityAuthorizationResolver.AuthorizationResult(false, false, null, null, null, null, false)
                : loaded;
    }

    private static int intValue(Environment env, String key, int defaultValue) {
        try {
            return Integer.parseInt(env.get(key, Integer.toString(defaultValue)));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static long longValue(Environment env, String key, long defaultValue) {
        try {
            return Long.parseLong(env.get(key, Long.toString(defaultValue)));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
