package com.everyarrow.eventengine.core.services.registry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaDbConfigTest {

    @Test
    void loadsDottedEnvironmentKeys() {
        Map<String, String> values = Map.of(
                "METADB.DB_HOST", "localhost",
                "METADB.DB_PORT", "15432",
                "METADB.DB_NAME", "postgres",
                "METADB.DB_USERNAME", "metadata_user",
                "METADB.DB_PASSWORD", "secret",
                "METADB.DB_SSL", "true"
        );

        MetaDbConfig config = MetaDbConfig.fromLookup(values::get).orElseThrow();

        assertEquals("localhost", config.host());
        assertEquals(15432, config.port());
        assertEquals("postgres", config.database());
        assertEquals("metadata_user", config.username());
        assertEquals("secret", config.password());
        assertTrue(config.ssl());
        assertFalse(config.allowRemotePlaintext());
    }

    @Test
    void keepsBackwardCompatibilityForUnderscoreAndUserNameAlias() {
        Map<String, String> values = Map.of(
                "METADB_DB_HOST", "127.0.0.1",
                "METADB_DB_NAME", "postgres",
                "METADB_DB_USER_NAME", "legacy_user",
                "METADB_DB_PASSWORD", "secret"
        );

        MetaDbConfig config = MetaDbConfig.fromLookup(values::get).orElseThrow();

        assertEquals("127.0.0.1", config.host());
        assertEquals(5432, config.port());
        assertEquals("postgres", config.database());
        assertEquals("legacy_user", config.username());
        assertFalse(config.ssl());
        assertFalse(config.allowRemotePlaintext());
    }

    @Test
    void returnsEmptyWhenRequiredValuesAreMissing() {
        Map<String, String> values = Map.of(
                "METADB.DB_HOST", "localhost",
                "METADB.DB_NAME", "postgres"
        );

        assertTrue(MetaDbConfig.fromLookup(values::get).isEmpty());
    }

    @Test
    void requiresExplicitSslForNonLoopbackHost() {
        Map<String, String> values = Map.of(
                "METADB.DB_HOST", "db.internal",
                "METADB.DB_NAME", "postgres",
                "METADB.DB_USERNAME", "metadata_user",
                "METADB.DB_PASSWORD", "secret"
        );

        assertThrows(IllegalStateException.class, () -> MetaDbConfig.fromLookup(values::get));
    }

    @Test
    void allowsExplicitPlaintextOverrideForNonLoopbackHost() {
        Map<String, String> values = Map.of(
                "METADB.DB_HOST", "db.internal",
                "METADB.DB_NAME", "postgres",
                "METADB.DB_USERNAME", "metadata_user",
                "METADB.DB_PASSWORD", "secret",
                "METADB.DB_SSL", "false",
                "EA_ALLOW_PLAINTEXT_DB", "true"
        );

        MetaDbConfig config = MetaDbConfig.fromLookup(values::get).orElseThrow();

        assertFalse(config.ssl());
        assertTrue(config.allowRemotePlaintext());
    }
}
