package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.core.config.EventManagerOperatingMode;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandlerException;
import com.everyarrow.utilities.db.ConnectionPool;
import com.everyarrow.utilities.db.SimpleConnection;
import com.everyarrow.utilities.environment.Environment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class DatabaseConnectionProvider {

    static final String ALLOW_PLAINTEXT_DB_CONFIG = "EA_ALLOW_PLAINTEXT_DB";

    private static final String DECLARED_PROFILES_CONFIG = "EA_DB_PROFILES";
    private static final Set<String> PROFILE_KEY_SUFFIXES = Set.of(
            ".DB_HOST", "_DB_HOST", ".DB_NAME", "_DB_NAME", ".DB_PROFILE", "_DB_PROFILE");

    private final Environment env;

    DatabaseConnectionProvider() {
        this(Environment.getInstance());
    }

    DatabaseConnectionProvider(Environment env) {
        this.env = env;
    }

    Connection getConnection() throws SQLException {
        requireDatabaseMode();
        if (hasUnprefixedDbConfig()) {
            requireSecureConnectionConfig(runtimeValue("DB_HOST"), runtimeValue("DB_SSL"), "DB_SSL",
                    runtimeValue(ALLOW_PLAINTEXT_DB_CONFIG));
            return ConnectionPool.getPostgresConnection();
        }

        String profile = normalize(env.get("DB_DEFAULT_PROFILE", ""));
        if (profile.isEmpty()) {
            profile = firstAvailableProfile();
        }
        if (profile.isEmpty()) {
            throw new ServiceCallHandlerException(
                    "No database configuration found. Expected DB_HOST, <PROFILE>.DB_HOST, "
                            + "an alias <PROFILE>.DB_PROFILE, or " + DECLARED_PROFILES_CONFIG);
        }

        DbProfile cfg = resolveProfile(profile);
        return profileConnection(cfg, cfg.dbName);
    }

    Connection getConnection(String dbName) throws SQLException {
        requireDatabaseMode();
        String requested = normalize(dbName);
        if (requested.isEmpty()) {
            return getConnection();
        }

        String matchedProfile = findProfileForDbName(requested);

        if (hasUnprefixedDbConfig()) {
            String configured = normalize(runtimeValue("DB_NAME"));
            if (!configured.isEmpty() && configured.equalsIgnoreCase(requested)) {
                requireSecureConnectionConfig(runtimeValue("DB_HOST"), runtimeValue("DB_SSL"), "DB_SSL",
                        runtimeValue(ALLOW_PLAINTEXT_DB_CONFIG));
                return ConnectionPool.getPostgresConnection();
            }

            if (!matchedProfile.isEmpty()) {
                DbProfile cfg = resolveProfile(matchedProfile);
                return profileConnection(cfg, requested);
            }

            DbProfile cfg = resolveUnprefixed();
            return profileConnection(cfg, requested);
        }

        if (!matchedProfile.isEmpty()) {
            DbProfile cfg = resolveProfile(matchedProfile);
            return profileConnection(cfg, requested);
        }

        String profile = normalize(env.get("DB_DEFAULT_PROFILE", ""));
        if (profile.isEmpty()) {
            profile = firstAvailableProfile();
        }
        if (profile.isEmpty()) {
            throw new ServiceCallHandlerException(
                    "No database configuration found for dbName=" + requested
                            + ". Expected DB_HOST or a profile with <PROFILE>.DB_NAME=" + requested
                            + ", optionally referenced by <PROFILE>.DB_PROFILE"
            );
        }

        DbProfile cfg = resolveProfile(profile);
        return profileConnection(cfg, requested);
    }

    private Connection profileConnection(DbProfile cfg, String database) throws SQLException {
        return SimpleConnection.getConnection(
                cfg.host,
                cfg.port,
                database,
                cfg.user,
                cfg.pass,
                cfg.useSSL,
                cfg.allowRemotePlaintext);
    }

    private void requireDatabaseMode() throws SQLException {
        if (EventManagerOperatingMode.fromEnvironment().isLocal()) {
            throw new SQLException("Database access is disabled while "
                    + EventManagerOperatingMode.CONFIG + "=local");
        }
    }

    static boolean resolveSsl(String host, String sslValue, String configKey, String allowPlaintextValue) {
        String normalizedHost = normalize(host);
        String normalizedSsl = normalize(sslValue);
        boolean loopback = isLoopbackHost(normalizedHost);
        boolean allowPlaintext = Boolean.parseBoolean(normalize(allowPlaintextValue));

        if (normalizedSsl.isEmpty()) {
            if (loopback) {
                return false;
            }
            throw new ServiceCallHandlerException(configKey + " must be explicitly true for non-loopback DB host "
                    + normalizedHost + " or " + ALLOW_PLAINTEXT_DB_CONFIG + "=true must be set");
        }

        boolean useSsl = Boolean.parseBoolean(normalizedSsl);
        if (!useSsl && !loopback && !allowPlaintext) {
            throw new ServiceCallHandlerException(configKey + "=false is not allowed for non-loopback DB host "
                    + normalizedHost + " unless " + ALLOW_PLAINTEXT_DB_CONFIG + "=true is set");
        }
        return useSsl;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasUnprefixedDbConfig() {
        return !runtimeValue("DB_HOST").isEmpty();
    }

    private DbProfile resolveUnprefixed() {
        String host = runtimeValue("DB_HOST");
        int port = Integer.parseInt(firstNonBlank(runtimeValue("DB_PORT"), "5432"));
        String dbName = runtimeValue("DB_NAME");
        String user = firstNonBlank(runtimeValue("DB_USERNAME"), runtimeValue("DB_USER"));
        String pass = runtimeValue("DB_PASSWORD");
        String allowPlaintextValue = runtimeValue(ALLOW_PLAINTEXT_DB_CONFIG);
        boolean useSSL = resolveSsl(runtimeValue("DB_HOST"), runtimeValue("DB_SSL"), "DB_SSL",
                allowPlaintextValue);
        return new DbProfile("", host, port, dbName, user, pass, useSSL,
                Boolean.parseBoolean(allowPlaintextValue));
    }

    private String firstAvailableProfile() {
        for (String profile : configuredProfiles()) {
            if (!resolvePhysicalProfile(profile).isEmpty()) {
                return profile;
            }
        }
        return "";
    }

    private String findProfileForDbName(String dbName) {
        Set<String> physicalProfiles = new LinkedHashSet<>();
        for (String profile : configuredProfiles()) {
            String physicalProfile = resolvePhysicalProfile(profile);
            if (physicalProfile.isEmpty() || !physicalProfiles.add(physicalProfile)) {
                continue;
            }
            String name = profileValue(physicalProfile, "DB_NAME");
            if (!name.isEmpty() && name.equalsIgnoreCase(dbName)) {
                return physicalProfile;
            }
        }
        return "";
    }

    private Set<String> configuredProfiles() {
        Set<String> profiles = new LinkedHashSet<>();
        String declaredProfiles = runtimeValue(DECLARED_PROFILES_CONFIG);
        if (!declaredProfiles.isBlank()) {
            for (String declaredProfile : declaredProfiles.split(",")) {
                String normalized = normalizeProfile(declaredProfile);
                if (!normalized.isEmpty()) {
                    profiles.add(normalized);
                }
            }
        }

        Set<String> discoveredProfiles = new TreeSet<>();
        for (String key : env.keys()) {
            String profile = profileFromKey(key);
            if (!profile.isEmpty()) {
                discoveredProfiles.add(profile);
            }
        }
        profiles.addAll(discoveredProfiles);
        return profiles;
    }

    private static String profileFromKey(String key) {
        String normalizedKey = normalizeProfile(key);
        for (String suffix : PROFILE_KEY_SUFFIXES) {
            if (normalizedKey.endsWith(suffix) && normalizedKey.length() > suffix.length()) {
                return normalizedKey.substring(0, normalizedKey.length() - suffix.length());
            }
        }
        return "";
    }

    private DbProfile resolveProfile(String profile) {
        String prefix = resolvePhysicalProfile(profile);
        if (prefix.isEmpty()) {
            throw new ServiceCallHandlerException("DB profile prefix is required");
        }

        String host = requireProfileValue(prefix, "DB_HOST");
        int port = Integer.parseInt(profileValue(prefix, "DB_PORT", "5432"));
        String dbName = requireProfileValue(prefix, "DB_NAME");
        String user = firstNonBlank(profileValue(prefix, "DB_USERNAME"), profileValue(prefix, "DB_USER"));
        if (user.isEmpty()) {
            throw new ServiceCallHandlerException("Missing database configuration: "
                    + prefix + ".DB_USERNAME, " + prefix + "_DB_USERNAME, "
                    + prefix + ".DB_USER, or " + prefix + "_DB_USER");
        }
        String pass = requireProfileValue(prefix, "DB_PASSWORD");
        String allowPlaintextValue = runtimeValue(ALLOW_PLAINTEXT_DB_CONFIG);
        boolean useSSL = resolveSsl(host, profileValue(prefix, "DB_SSL", runtimeValue("DB_SSL")), prefix + ".DB_SSL",
                allowPlaintextValue);

        return new DbProfile(prefix, host, port, dbName, user, pass, useSSL,
                Boolean.parseBoolean(allowPlaintextValue));
    }

    String resolvePhysicalProfileNameForTest(String profile) {
        return resolvePhysicalProfile(profile);
    }

    String findProfileForDbNameForTest(String dbName) {
        return findProfileForDbName(dbName);
    }

    private String resolvePhysicalProfile(String profile) {
        String current = normalizeProfile(profile);
        if (current.isEmpty()) {
            return "";
        }

        Set<String> visited = new LinkedHashSet<>();
        while (!current.isEmpty()) {
            if (!visited.add(current)) {
                throw new ServiceCallHandlerException("DB profile indirection cycle detected: "
                        + String.join(" -> ", visited) + " -> " + current);
            }

            if (!profileValue(current, "DB_HOST").isEmpty()) {
                return current;
            }

            String next = normalizeProfile(profileValue(current, "DB_PROFILE"));
            if (next.isEmpty()) {
                return "";
            }
            current = next;
        }
        return "";
    }

    private String requireProfileValue(String profile, String suffix) {
        String value = profileValue(profile, suffix);
        if (value.isEmpty()) {
            throw new ServiceCallHandlerException("Missing database configuration: "
                    + profile + "." + suffix + " or " + profile + "_" + suffix);
        }
        return value;
    }

    private String profileValue(String profile, String suffix) {
        return profileValue(profile, suffix, "");
    }

    private String profileValue(String profile, String suffix, String defaultValue) {
        String dottedRuntime = normalize(System.getenv(profile + "." + suffix));
        if (!dottedRuntime.isEmpty()) {
            return dottedRuntime;
        }
        String underscoredRuntime = normalize(System.getenv(profile + "_" + suffix));
        if (!underscoredRuntime.isEmpty()) {
            return underscoredRuntime;
        }
        String dotted = normalize(env.get(profile + "." + suffix, ""));
        if (!dotted.isEmpty()) {
            return dotted;
        }
        String underscored = normalize(env.get(profile + "_" + suffix, ""));
        if (!underscored.isEmpty()) {
            return underscored;
        }
        return defaultValue;
    }

    private String runtimeValue(String key) {
        return firstNonBlank(env.get(key, ""), System.getenv(key));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalizeProfile(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }

    private static void requireSecureConnectionConfig(String host,
                                                      String sslValue,
                                                      String configKey,
                                                      String allowPlaintextValue) {
        resolveSsl(host, sslValue, configKey, allowPlaintextValue);
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = normalize(host).toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("[::1]");
    }

    private record DbProfile(String profile,
                             String host,
                             int port,
                             String dbName,
                             String user,
                             String pass,
                             boolean useSSL,
                             boolean allowRemotePlaintext) {
    }
}
