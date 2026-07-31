package com.everyarrow.eventengine.boot;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManagerImpl;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Embedded Artemis broker bound to a TCP address (localhost by default).
 *
 * This is used to make local dev and tests deterministic without requiring Docker DNS.
 * It starts only when JMS_BROKER_URL points to tcp://localhost:<port> or tcp://127.0.0.1:<port>.
 */
public final class EmbeddedArtemisTcpBroker implements AutoCloseable {

    private static final String EMBEDDED_BROKER_ROLE = "ea-embedded-artemis";
    private static final long EMBEDDED_GLOBAL_MAX_SIZE_BYTES = 512L * 1024L * 1024L;
    private static final int EMBEDDED_PAGE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final int EMBEDDED_MAX_DISK_USAGE_PERCENT = 99;
    private static final String ARTEMIS_RUNTIME_DIR_PROPERTY = "ea.artemis.dir";
    private static final String ARTEMIS_RUNTIME_DIR_ENV = "EA_ARTEMIS_DIR";

    private final EmbeddedActiveMQ embedded;
    private final String brokerUrl;

    private EmbeddedArtemisTcpBroker(EmbeddedActiveMQ embedded, String brokerUrl) {
        this.embedded = embedded;
        this.brokerUrl = brokerUrl;
    }

    public String brokerUrl() {
        return brokerUrl;
    }

    public static EmbeddedArtemisTcpBroker startIfLocalTcp(String brokerUrl) {
        return startIfLocalTcp(brokerUrl, null, null);
    }

    public static EmbeddedArtemisTcpBroker startIfLocalTcp(String brokerUrl, String username, String password) {
        return startIfLocalTcp(brokerUrl, username, password, false);
    }

    public static EmbeddedArtemisTcpBroker startIfLocalTcp(String brokerUrl,
                                                           String username,
                                                           String password,
                                                           boolean allowPublicBind) {
        BrokerPlan plan = plan(brokerUrl, username, password, allowPublicBind);
        if (plan == null) {
            return null;
        }

        String host = plan.host();
        int port = plan.port();
        boolean securityEnabled = plan.securityEnabled();

        try {
            Path base = runtimeDirectory(port);
            Files.createDirectories(base);

            // Ensure required subdirectories exist deterministically.
            Files.createDirectories(base.resolve("bindings"));
            Files.createDirectories(base.resolve("journal"));
            Files.createDirectories(base.resolve("paging"));
            Files.createDirectories(base.resolve("largemessages"));

            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setName("ea-embedded-artemis");
            // Runner commands and status events must survive an Event Manager or host restart.
            cfg.setPersistenceEnabled(true);
            cfg.setGlobalMaxSize(EMBEDDED_GLOBAL_MAX_SIZE_BYTES);
            cfg.setMaxDiskUsage(EMBEDDED_MAX_DISK_USAGE_PERCENT);
            cfg.setMinDiskFree(1L);

            cfg.setSecurityEnabled(securityEnabled);
            if (securityEnabled) {
                cfg.putSecurityRoles("#", Set.of(embeddedBrokerRole()));
            }

            cfg.setBindingsDirectory(base.resolve("bindings").toString());
            cfg.setJournalDirectory(base.resolve("journal").toString());
            cfg.setPagingDirectory(base.resolve("paging").toString());
            cfg.setLargeMessagesDirectory(base.resolve("largemessages").toString());

            cfg.addAcceptorConfiguration("netty", "tcp://" + plan.bindHost() + ":" + port);

            // Make queue/address creation easy for tests and local dev.
            AddressSettings settings = new AddressSettings();
            settings.setAutoCreateAddresses(true);
            settings.setAutoCreateQueues(true);
            settings.setAutoDeleteAddresses(false);
            settings.setAutoDeleteQueues(false);
            settings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
            settings.setMaxSizeBytes(EMBEDDED_GLOBAL_MAX_SIZE_BYTES);
            settings.setPageSizeBytes(EMBEDDED_PAGE_SIZE_BYTES);
            cfg.setAddressSettings(Collections.singletonMap("#", settings));

            EmbeddedActiveMQ embedded = new EmbeddedActiveMQ();
            embedded.setConfiguration(cfg);
            if (securityEnabled) {
                embedded.setSecurityManager(securityManager(username, password));
            }
            embedded.start();

            System.out.println("[EngineBootstrap] Embedded Artemis started at tcp://" + plan.bindHost() + ":" + port
                    + " authentication=" + (securityEnabled ? "enabled" : "disabled"));
            return new EmbeddedArtemisTcpBroker(embedded, "tcp://" + host + ":" + port);

        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded Artemis broker at tcp://" + host + ":" + port, e);
        }
    }

    static BrokerPlan plan(String brokerUrl, String username, String password, boolean allowPublicBind) {
        if (brokerUrl == null) return null;
        String url = brokerUrl.trim();
        if (url.isEmpty()) return null;

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            System.out.println("[EngineBootstrap] Invalid JMS_BROKER_URL. Not starting embedded Artemis. value=" + url);
            return null;
        }

        if (!"tcp".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }

        String host = uri.getHost();
        if (host == null) return null;

        boolean local = isLoopbackHost(host);
        boolean securityEnabled = hasText(username) && hasText(password);
        if (!local && !allowPublicBind) return null;
        if (!local && !securityEnabled) {
            throw new IllegalStateException("Embedded Artemis public TCP binding requires JMS_USERNAME and JMS_PASSWORD");
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 61616;
        String bindHost = local && "localhost".equalsIgnoreCase(host) ? "127.0.0.1" : host;
        return new BrokerPlan(host, bindHost, port, securityEnabled);
    }

    static Path runtimeDirectory(int port) {
        String configured = firstText(System.getProperty(ARTEMIS_RUNTIME_DIR_PROPERTY), System.getenv(ARTEMIS_RUNTIME_DIR_ENV));
        Path root = configured == null
                ? Path.of(System.getProperty("user.dir"), ".ea-artemis")
                : Path.of(configured);
        return root.resolve("ea-artemis-" + port);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private static boolean isLoopbackHost(String host) {
        return "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    record BrokerPlan(String host, String bindHost, int port, boolean securityEnabled) {}

    private static ActiveMQSecurityManagerImpl securityManager(String username, String password) {
        SecurityConfiguration securityConfiguration = new SecurityConfiguration();
        securityConfiguration.addUser(username.trim(), password.trim());
        securityConfiguration.addRole(username.trim(), EMBEDDED_BROKER_ROLE);
        return new ActiveMQSecurityManagerImpl(securityConfiguration);
    }

    private static Role embeddedBrokerRole() {
        Role role = new Role();
        role.setName(EMBEDDED_BROKER_ROLE);
        role.setSend(true);
        role.setConsume(true);
        role.setCreateAddress(true);
        role.setCreateDurableQueue(true);
        role.setCreateNonDurableQueue(true);
        role.setBrowse(true);
        return role;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public void close() {
        try {
            embedded.stop();
        } catch (Exception ignored) {}
    }
}
