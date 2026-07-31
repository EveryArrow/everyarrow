/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal deterministic SemVer and interval support for function contracts.
 */
final class FunctionVersionRange {

    private static final Pattern VERSION = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$");

    private final SemanticVersion lower;
    private final boolean includeLower;
    private final SemanticVersion upper;
    private final boolean includeUpper;

    private FunctionVersionRange(SemanticVersion lower,
                                 boolean includeLower,
                                 SemanticVersion upper,
                                 boolean includeUpper) {
        this.lower = lower;
        this.includeLower = includeLower;
        this.upper = upper;
        this.includeUpper = includeUpper;
    }

    static FunctionVersionRange parse(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidFunctionOverrideException(field + " is required");
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("(")) {
            SemanticVersion exact = parseVersion(trimmed, field);
            return new FunctionVersionRange(exact, true, exact, true);
        }
        if (!(trimmed.endsWith("]") || trimmed.endsWith(")"))) {
            throw invalidRange(field, value);
        }
        String[] endpoints = trimmed.substring(1, trimmed.length() - 1)
                .split(",", -1);
        if (endpoints.length != 2) {
            throw invalidRange(field, value);
        }
        SemanticVersion lower = endpoints[0].isBlank()
                ? null
                : parseVersion(endpoints[0].trim(), field);
        SemanticVersion upper = endpoints[1].isBlank()
                ? null
                : parseVersion(endpoints[1].trim(), field);
        if (lower == null && upper == null) {
            throw invalidRange(field, value);
        }
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            throw invalidRange(field, value);
        }
        return new FunctionVersionRange(
                lower,
                trimmed.startsWith("["),
                upper,
                trimmed.endsWith("]"));
    }

    static void validateVersion(String value, String field) {
        parseVersion(value, field);
    }

    boolean contains(String version, String field) {
        SemanticVersion candidate = parseVersion(version, field);
        if (lower != null) {
            int comparison = candidate.compareTo(lower);
            if (comparison < 0 || (comparison == 0 && !includeLower)) {
                return false;
            }
        }
        if (upper != null) {
            int comparison = candidate.compareTo(upper);
            if (comparison > 0 || (comparison == 0 && !includeUpper)) {
                return false;
            }
        }
        return true;
    }

    private static SemanticVersion parseVersion(String value, String field) {
        if (value == null) {
            throw new InvalidFunctionOverrideException(field + " is required");
        }
        Matcher matcher = VERSION.matcher(value.trim());
        if (!matcher.matches()) {
            throw new InvalidFunctionOverrideException(
                    field + " must use semantic version major.minor.patch: " + value);
        }
        return new SemanticVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    private static InvalidFunctionOverrideException invalidRange(
            String field,
            String value) {
        return new InvalidFunctionOverrideException(
                field + " must be an exact semantic version or an interval "
                        + "such as [1.0.0,2.0.0): " + value);
    }

    private record SemanticVersion(int major, int minor, int patch)
            implements Comparable<SemanticVersion> {

        @Override
        public int compareTo(SemanticVersion other) {
            int majorComparison = Integer.compare(major, other.major);
            if (majorComparison != 0) {
                return majorComparison;
            }
            int minorComparison = Integer.compare(minor, other.minor);
            if (minorComparison != 0) {
                return minorComparison;
            }
            return Integer.compare(patch, other.patch);
        }
    }
}
