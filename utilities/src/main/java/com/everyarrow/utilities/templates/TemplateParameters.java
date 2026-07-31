/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates;

final class TemplateParameters {
    private final String rawParameters;

    TemplateParameters(String rawParameters) {
        this.rawParameters = rawParameters == null ? "" : rawParameters;
    }

    String value(String key) {
        return value(key, null);
    }

    String value(String key, String defaultValue) {
        String[] parameterPairs = rawParameters.split(",");
        for (String parameter : parameterPairs) {
            String[] keyValue = parameter.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }

            String parameterKey = keyValue[0].trim();
            String parameterValue = keyValue[1].trim();
            if (!parameterKey.equals(key)) {
                continue;
            }

            if (parameterValue.startsWith("\"") && parameterValue.endsWith("\"") && parameterValue.length() >= 2) {
                parameterValue = parameterValue.substring(1, parameterValue.length() - 1);
            }
            return unescapeParameterValue(parameterValue);
        }
        return defaultValue;
    }

    private String unescapeParameterValue(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
