package com.everyarrow.eventengine.core.services.runtime;

import com.everyarrow.eventengine.spi.handler.ServiceCallHandlerException;
import com.everyarrow.utilities.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresHelperSecurityConfigTest {

    @AfterEach
    void resetEnvironment() {
        Environment.reset();
    }

    @Test
    void loopbackDatabaseCanOmitSslForLocalDevelopment() {
        assertFalse(PostgresHelper.resolveSsl("localhost", "", "DB_SSL", ""));
        assertFalse(PostgresHelper.resolveSsl("127.0.0.1", "false", "DB_SSL", ""));
        assertFalse(DatabaseConnectionProvider.resolveSsl("::1", "", "DB_SSL", ""));
    }

    @Test
    void nonLoopbackDatabaseRequiresExplicitSsl() {
        assertThrows(ServiceCallHandlerException.class,
                () -> PostgresHelper.resolveSsl("db.internal", "", "DB_SSL", ""));
        assertThrows(ServiceCallHandlerException.class,
                () -> PostgresHelper.resolveSsl("db.internal", "false", "DB_SSL", ""));
        assertThrows(ServiceCallHandlerException.class,
                () -> DatabaseConnectionProvider.resolveSsl("db.internal", "", "DB_SSL", ""));
    }

    @Test
    void nonLoopbackDatabaseAllowsExplicitSslOrExplicitPlaintextOverride() {
        assertTrue(PostgresHelper.resolveSsl("db.internal", "true", "DB_SSL", ""));
        assertFalse(PostgresHelper.resolveSsl("db.internal", "false", "DB_SSL", "true"));
        assertTrue(DatabaseConnectionProvider.resolveSsl("db.internal", "true", "DB_SSL", ""));
    }

    @Test
    void logicalProfileResolvesToDiscoveredPhysicalDatabaseProfile() {
        Environment.put("PLATFORM.DB_HOST", "localhost");
        Environment.put("PLATFORM.DB_NAME", "platform_data");
        Environment.put("ACCOUNT.DB_PROFILE", "PLATFORM");

        DatabaseConnectionProvider provider = new DatabaseConnectionProvider(Environment.getInstance());

        assertEquals("PLATFORM", provider.resolvePhysicalProfileNameForTest("ACCOUNT"));
        assertEquals("PLATFORM", provider.findProfileForDbNameForTest("platform_data"));
    }

    @Test
    void directPhysicalProfileStillWorks() {
        Environment.put("ANALYTICS_RUNTIME.DB_HOST", "localhost");
        Environment.put("ANALYTICS_RUNTIME.DB_NAME", "analytics");

        DatabaseConnectionProvider provider = new DatabaseConnectionProvider(Environment.getInstance());

        assertEquals("ANALYTICS_RUNTIME", provider.resolvePhysicalProfileNameForTest("ANALYTICS_RUNTIME"));
    }

    @Test
    void profileIndirectionCycleFailsClearly() {
        Environment.put("ALPHA.DB_PROFILE", "BETA");
        Environment.put("BETA.DB_PROFILE", "ALPHA");

        DatabaseConnectionProvider provider = new DatabaseConnectionProvider(Environment.getInstance());

        assertThrows(ServiceCallHandlerException.class,
                () -> provider.resolvePhysicalProfileNameForTest("ALPHA"));
    }

    @Test
    void localEventManagerModeRejectsDatabaseAccessBeforeConnectionConfigurationIsRead() {
        Environment.put("EA_EVENT_MANAGER_MODE", "local");
        Environment.put("DB_HOST", "database-that-must-not-be-contacted.invalid");

        DatabaseConnectionProvider provider = new DatabaseConnectionProvider(Environment.getInstance());

        SQLException error = assertThrows(SQLException.class, provider::getConnection);
        assertTrue(error.getMessage().contains("EA_EVENT_MANAGER_MODE=local"));
    }
}
