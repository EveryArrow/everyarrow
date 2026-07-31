package com.everyarrow.eventengine.tests.eventtype;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.model.DbDiscoveredService;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.tests.EngineHarness;
import com.everyarrow.utilities.db.SimpleConnection;
import com.everyarrow.utilities.environment.Environment;

import java.sql.Connection;
import java.sql.Statement;

public final class DbGenericProcessorHelloWorld {

    private static final String SCHEMA = "public";
    private static final String FUNCTION = "hello_world";
    public static final String ROUTE = "/tests/db/hello-world";
    private static volatile boolean registered = false;
    private static Connection adminConn;

    private DbGenericProcessorHelloWorld() {
    }

    public static synchronized void ensureRegistered(EngineHarness harness) throws Exception {
        if (registered)
            return;

        // Defensive: avoid Environment RAW_ENV not being initialized in this JVM
        // (also avoids the ConcurrentHashMap null-cache NPE path when keys are absent)
        Environment.initFromResource("ea.env");

        Engine engine = harness.getEngine();
        DbConn cfg = resolveDbConn();

        // One connection for setup + teardown (DDL only)
        adminConn = SimpleConnection.getConnection(
                cfg.host, cfg.port, cfg.dbName, cfg.user, cfg.pass, cfg.useSSL);

        // Create function
        try (Statement s = adminConn.createStatement()) {
            s.execute("""
                        create or replace function public.hello_world(p jsonb)
                        returns jsonb
                        language sql
                        as $$
                          select jsonb_build_object('message', 'Hello World');
                        $$;
                    """);
        }

        // Register route -> serviceType -> GenericProcessHandler
        String serviceType = cfg.dbName + "." + SCHEMA + "." + FUNCTION;

        DbDiscoveredService discovered = new DbDiscoveredService(
                cfg.dbName,
                SCHEMA,
                FUNCTION,
                serviceType,
                "GET",
                "/hello-world",
                "Test hello world",
                null,
                null,
                null);

        ServiceCallDefinition def = ServiceCallDefinition.fromDb(discovered);
        engine.getRegistry().register(serviceType, new GenericProcessHandler(), def);

        registered = true;
    }

    /** Called by the outer test's @AfterAll. */
    public static synchronized void cleanup() {
        if (adminConn != null) {
            try (Statement s = adminConn.createStatement()) {
                s.execute("drop function if exists public.hello_world(jsonb);");
            } catch (Exception ignored) {
            } finally {
                SimpleConnection.closeConnection(adminConn);
                adminConn = null;
            }
        }
        registered = false;
    }

    private static DbConn resolveDbConn() {
        Environment env = Environment.getInstance();

        // Optional overrides (local dev)
        String testUrl = System.getenv("TEST_URL"); // jdbc:postgresql://host:port/dbname
        String testUser = System.getenv("TEST_USER");
        String testPass = System.getenv("TEST_PASS");

        if (!isBlank(testUrl)) {
            return parseJdbcUrlIntoDbConn(testUrl, testUser, testPass, env);
        }

        // Primary: TEST.DB_* (these keys exist in your ea.env)
        String host = env.require("TEST.DB_HOST");
        int port = Integer.parseInt(env.get("TEST.DB_PORT", "5432"));
        String dbName = env.require("TEST.DB_NAME");
        String user = env.require("TEST.DB_USERNAME");
        String pass = env.require("TEST.DB_PASSWORD");

        return new DbConn(host, port, dbName, user, pass, false);
    }

    private static DbConn parseJdbcUrlIntoDbConn(String jdbcUrl, String userOverride, String passOverride,
            Environment env) {
        String prefix = "jdbc:postgresql://";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            throw new IllegalStateException("TEST_URL must start with " + prefix + " but was: " + jdbcUrl);
        }

        String rest = jdbcUrl.substring(prefix.length()); // host:port/db?x=y
        int slash = rest.indexOf('/');
        if (slash < 0)
            throw new IllegalStateException("TEST_URL missing /dbname segment: " + jdbcUrl);

        String hostPort = rest.substring(0, slash);
        String dbAndMaybeParams = rest.substring(slash + 1);
        int q = dbAndMaybeParams.indexOf('?');
        String dbName = (q >= 0) ? dbAndMaybeParams.substring(0, q) : dbAndMaybeParams;

        String host;
        int port = 5432;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0) {
            host = hostPort.substring(0, colon);
            port = Integer.parseInt(hostPort.substring(colon + 1));
        } else {
            host = hostPort;
        }

        String user = !isBlank(userOverride) ? userOverride : env.require("PERCH.DB_USERNAME");
        String pass = !isBlank(passOverride) ? passOverride : env.require("PERCH.DB_PASSWORD");

        return new DbConn(host, port, dbName, user, pass, false);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record DbConn(String host, int port, String dbName, String user, String pass, boolean useSSL) {
    }
}
