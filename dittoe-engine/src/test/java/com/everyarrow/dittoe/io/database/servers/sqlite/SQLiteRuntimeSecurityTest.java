package com.everyarrow.dittoe.io.database.servers.sqlite;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteRuntimeSecurityTest {
    private static final SQLiteVersion MINIMUM_SECURE_VERSION = SQLiteVersion.parse("3.53.2");

    @Test
    void embedsPatchedNativeRuntimeWithFts5Support() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            SQLiteVersion runtimeVersion = readRuntimeVersion(statement);
            Set<String> compileOptions = readCompileOptions(statement);

            assertTrue(
                    runtimeVersion.compareTo(MINIMUM_SECURE_VERSION) >= 0,
                    () -> "SQLite runtime " + runtimeVersion
                            + " is older than required " + MINIMUM_SECURE_VERSION);
            assertTrue(
                    compileOptions.contains("ENABLE_FTS5"),
                    () -> "Expected SQLite FTS5 support; compile options were " + compileOptions);
        }
    }

    private SQLiteVersion readRuntimeVersion(Statement statement) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT sqlite_version()")) {
            assertTrue(result.next(), "sqlite_version() returned no row");
            return SQLiteVersion.parse(result.getString(1));
        }
    }

    private Set<String> readCompileOptions(Statement statement) throws Exception {
        Set<String> options = new HashSet<>();
        try (ResultSet result = statement.executeQuery("PRAGMA compile_options")) {
            while (result.next()) {
                options.add(result.getString(1));
            }
        }
        return Set.copyOf(options);
    }

    private record SQLiteVersion(int major, int minor, int patch)
            implements Comparable<SQLiteVersion> {
        private static SQLiteVersion parse(String value) {
            String[] parts = value.split("\\.");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Unsupported SQLite version: " + value);
            }
            return new SQLiteVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }

        @Override
        public int compareTo(SQLiteVersion other) {
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

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
