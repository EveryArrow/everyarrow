/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.security;

import com.everyarrow.utilities.environment.Environment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves optional console targets for hosted platform surfaces.
 *
 * A console id scopes UI/application-surface authorization when a call comes
 * from a console. Product and service authorization can still proceed without
 * one by using service capabilities.
 */
public final class SecurityApplicationTargets {

    public static final String EVENT_MANAGER_CONSOLE_ID = "EVENT_MANAGER_CONSOLE_ID";
    public static final String HOSTED_APPLICATION_KEYS_CONFIG = "EA_EVENT_MANAGER_HOSTED_CONSOLE_KEYS";
    public static final String PLUGIN_SCOPED_APPLICATION_KEYS_CONFIG = "EA_SECURITY_PLUGIN_SCOPED_CONSOLE_KEYS";

    private static final String DEFAULT_HOSTED_APPLICATION_KEYS = "SECURITY,PERCH";
    private static final Pattern ENV_SUFFIX_PATTERN = Pattern.compile("_(DEV|TEST|PROD|STAGE|STAGING|QA|LOCAL)$");

    private SecurityApplicationTargets() {}

    public static String normalizeApplicationKey(String value) {
        if (isBlank(value)) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        normalized = ENV_SUFFIX_PATTERN.matcher(normalized).replaceFirst("");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if ("EA_SECURITY".equals(normalized)) {
            return "SECURITY";
        }
        return normalized.isBlank() ? null : normalized;
    }

    public static String resolveConsoleId(String applicationKey, Environment environment) {
        Environment resolvedEnvironment = environment == null ? Environment.getInstance() : environment;
        for (String candidate : candidateConfigKeys(applicationKey, resolvedEnvironment)) {
            String value = resolvedEnvironment.get(candidate);
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public static String expectedConfigDescription(String applicationKey, Environment environment) {
        Environment resolvedEnvironment = environment == null ? Environment.getInstance() : environment;
        return String.join(" or ", candidateConfigKeys(applicationKey, resolvedEnvironment));
    }

    public static String resolveSecuredItem(String serviceType, String applicationKey, Environment environment) {
        String normalized = normalizeApplicationKey(applicationKey);
        if (!isBlank(normalized) && pluginScopedApplicationKeys(environment).contains(normalized)) {
            return "plugin:" + normalized.toLowerCase(Locale.ROOT);
        }
        return serviceType;
    }

    private static List<String> candidateConfigKeys(String applicationKey, Environment environment) {
        String normalized = normalizeApplicationKey(applicationKey);
        if (isBlank(normalized)) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        if (hostedApplicationKeys(environment).contains(normalized)) {
            candidates.add(EVENT_MANAGER_CONSOLE_ID);
        }
        candidates.add(normalized + "_CONSOLE_ID");
        candidates.add("CONSOLE_ID");
        return candidates.stream().distinct().toList();
    }

    private static Set<String> hostedApplicationKeys(Environment environment) {
        Environment resolvedEnvironment = environment == null ? Environment.getInstance() : environment;
        String configured = resolvedEnvironment.get(HOSTED_APPLICATION_KEYS_CONFIG, DEFAULT_HOSTED_APPLICATION_KEYS);
        return parseKeys(configured);
    }

    private static Set<String> pluginScopedApplicationKeys(Environment environment) {
        Environment resolvedEnvironment = environment == null ? Environment.getInstance() : environment;
        String configured = resolvedEnvironment.get(PLUGIN_SCOPED_APPLICATION_KEYS_CONFIG, "");
        if (isBlank(configured)) {
            return hostedApplicationKeys(resolvedEnvironment);
        }
        return parseKeys(configured);
    }

    private static Set<String> parseKeys(String value) {
        Set<String> keys = new LinkedHashSet<>();
        if (isBlank(value)) {
            return keys;
        }

        for (String part : value.split(",")) {
            String normalized = normalizeApplicationKey(part);
            if (!isBlank(normalized)) {
                keys.add(normalized);
            }
        }
        return keys;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
