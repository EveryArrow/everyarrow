/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.templates.providers.postgres.procedures.select;

import java.util.Locale;

final class PostgresSelectIdentifiers {

    private PostgresSelectIdentifiers() {
    }

    static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("PostgreSQL identifier is required");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    static String quoteJsonKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("JSON key is required");
        }
        return "'" + key.replace("'", "''") + "'";
    }

    static String functionIdentifier(String tableName, String suffix) {
        return quoteIdentifier((tableName + suffix).toLowerCase(Locale.ROOT));
    }
}
