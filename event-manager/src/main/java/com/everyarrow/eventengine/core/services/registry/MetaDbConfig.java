package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.utilities.environment.Environment;

import java.util.Optional;
import java.util.function.Function;

/**
 * Normalizes metadata-database configuration across runtime and tooling.
 */
public record MetaDbConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean ssl,
        boolean allowRemotePlaintext
) {
    static final String ALLOW_PLAINTEXT_DB_CONFIG = "EA_ALLOW_PLAINTEXT_DB";

    public static Optional<MetaDbConfig> fromEnvironment(Environment environment) {
        Environment env = environment == null ? Environment.getInstance() : environment;
        return fromLookup(key -> env.get(key, ""));
    }

    static Optional<MetaDbConfig> fromLookup(Function<String, String> lookup) {
        if (lookup == null) {
            return Optional.empty();
        }

        String host = firstPresent(lookup, "METADB.DB_HOST", "METADB_DB_HOST");
        String database = firstPresent(lookup, "METADB.DB_NAME", "METADB_DB_NAME");
        String username = firstPresent(
                lookup,
                "METADB.DB_USERNAME",
                "METADB.DB_USER_NAME",
                "METADB_DB_USERNAME",
                "METADB_DB_USER_NAME"
        );
        String password = firstPresent(lookup, "METADB.DB_PASSWORD", "METADB_DB_PASSWORD");

        if (isBlank(host) || isBlank(database) || isBlank(username) || isBlank(password)) {
            return Optional.empty();
        }

        int port = parsePort(firstPresent(lookup, "METADB.DB_PORT", "METADB_DB_PORT"));
        String allowPlaintextValue = firstPresent(lookup, ALLOW_PLAINTEXT_DB_CONFIG);
        boolean ssl = resolveSsl(
                host,
                firstPresent(lookup, "METADB.DB_SSL", "METADB_DB_SSL"),
                "METADB.DB_SSL",
                allowPlaintextValue);
        boolean allowRemotePlaintext = !ssl && !isLoopbackHost(host) && parseBoolean(allowPlaintextValue);

        return Optional.of(new MetaDbConfig(host, port, database, username, password, ssl, allowRemotePlaintext));
    }

    private static String firstPresent(Function<String, String> lookup, String... keys) {
        for (String key : keys) {
            String value = lookup.apply(key);
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static int parsePort(String value) {
        if (isBlank(value)) {
            return 5432;
        }
        return Integer.parseInt(value.trim());
    }

    static boolean resolveSsl(String host, String sslValue, String configKey, String allowPlaintextValue) {
        String normalizedHost = host == null ? "" : host.trim();
        String normalizedSsl = sslValue == null ? "" : sslValue.trim();
        boolean loopback = isLoopbackHost(normalizedHost);
        boolean allowPlaintext = parseBoolean(allowPlaintextValue);

        if (normalizedSsl.isEmpty()) {
            if (loopback) {
                return false;
            }
            throw new IllegalStateException(configKey + " must be explicitly true for non-loopback DB host "
                    + normalizedHost + " or " + ALLOW_PLAINTEXT_DB_CONFIG + "=true must be set");
        }

        boolean useSsl = Boolean.parseBoolean(normalizedSsl);
        if (!useSsl && !loopback && !allowPlaintext) {
            throw new IllegalStateException(configKey + "=false is not allowed for non-loopback DB host "
                    + normalizedHost + " unless " + ALLOW_PLAINTEXT_DB_CONFIG + "=true is set");
        }
        return useSsl;
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase();
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("[::1]");
    }

    private static boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value == null ? "" : value.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
