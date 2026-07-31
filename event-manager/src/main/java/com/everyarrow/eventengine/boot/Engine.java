/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.boot;

import com.everyarrow.eventengine.core.credits.ExecutionCreditGate;
import com.everyarrow.eventengine.core.credits.ExecutionCreditGateFactory;
import com.everyarrow.eventengine.core.credits.ExecutionCreditHook;
import com.everyarrow.eventengine.core.config.EventManagerOperatingMode;
import com.everyarrow.eventengine.core.adapters.ProtocolAdapterManager;
import com.everyarrow.eventengine.core.adapters.external.websocket.SocketIoGatewayServlet;
import com.everyarrow.eventengine.core.hooks.HookManager;
import com.everyarrow.eventengine.core.hooks.SecurityHook;
import com.everyarrow.eventengine.core.hooks.SecurityProviderRegistry;
import com.everyarrow.eventengine.core.routing.RouteConventionValidator;
import com.everyarrow.eventengine.core.routing.SecurityMetadataValidator;
import com.everyarrow.eventengine.core.security.SecurityDecisionCache;
import com.everyarrow.eventengine.core.services.registry.DbServiceRegistryLoader;
import com.everyarrow.eventengine.core.services.registry.DbServiceRegistryRefresher;
import com.everyarrow.eventengine.core.services.registry.FunctionRegistrationOrigin;
import com.everyarrow.eventengine.core.services.registry.JsonServiceRegistrySyncServiceImpl;
import com.everyarrow.eventengine.core.services.registry.LocalRegistrySnapshotStore;
import com.everyarrow.eventengine.core.services.registry.MetaDbConfig;
import com.everyarrow.eventengine.core.services.registry.RegistryRuntimeStatus;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.plugins.EnginePluginContextImpl;
import com.everyarrow.eventengine.core.services.plugins.PluginRootResolver;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallResponse;
import com.everyarrow.eventengine.spi.plugin.EnginePluginContext;
import com.everyarrow.eventengine.spi.services.JsonServiceRegistrySyncService;
import com.everyarrow.eventengine.spi.services.RealtimeGateway;
import com.everyarrow.utilities.db.SimpleConnection;
import com.everyarrow.utilities.environment.Environment;
import com.everyarrow.eventengine.core.services.plugins.PluginManager;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Core Event Engine runtime.
 */
public class Engine {

    private final ServiceRegistry registry;
    private final HookManager hookManager;
    private final PluginManager pluginManager;
    private final ServiceCallPipeline serviceCallPipeline;
    private final JsonServiceRegistrySyncService jsonServiceRegistrySyncService;
    private final SecurityDecisionCache securityDecisionCache;
    private final SecurityProviderRegistry securityProviderRegistry;
    private final File pluginRoot;
    private volatile EnginePluginContext pluginContext;
    private volatile SocketIoGatewayServlet realtimeGateway;

    private final Object lifecycleMonitor = new Object();
    private volatile boolean running = false;
    private volatile boolean initialized = false;
    private volatile RegistryRuntimeStatus registryRuntimeStatus = RegistryRuntimeStatus.notInitialized();

    public Engine() {
        this(PluginRootResolver.resolve());
    }

    public Engine(File pluginRoot) {
        this(pluginRoot, ExecutionCreditGateFactory.create());
    }

    public Engine(File pluginRoot, ExecutionCreditGate executionCreditGate) {
        this.pluginRoot = Objects.requireNonNull(pluginRoot, "pluginRoot is required");
        this.registry = new ServiceRegistry();
        this.hookManager = new HookManager(this);
        this.securityDecisionCache = SecurityDecisionCache.fromEnvironment();
        this.securityProviderRegistry = new SecurityProviderRegistry();
        this.hookManager.register(new SecurityHook(this, securityProviderRegistry));
        this.hookManager.register(new ExecutionCreditHook(Objects.requireNonNull(
                executionCreditGate,
                "executionCreditGate is required")));
        this.pluginManager = new PluginManager(this.registry, this.pluginRoot);
        this.pluginManager.setEngine(this);
        this.serviceCallPipeline = new ServiceCallPipeline(this, registry, hookManager, pluginManager);
        this.jsonServiceRegistrySyncService = new JsonServiceRegistrySyncServiceImpl(this, this.registry);
    }

    /**
     * Initialize by scanning services and plugins.
     */
    public synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }

        // 1) Discover all handlers and their annotation metadata
        pluginManager.loadPlugins();

        // 2) Refresh/load executable registry metadata from the database source of truth.
        refreshAndLoadDbServiceRegistryIfConfigured();

        // 3) Require every declared override target after all local, server,
        // and snapshot candidates have been loaded.
        registry.validateExplicitOverrides();

        // 4) Validate annotation metadata
        RouteConventionValidator.validate(this);
        SecurityMetadataValidator.validate(this);
        initialized = true;
    }

    private void refreshAndLoadDbServiceRegistryIfConfigured() {
        Environment environment = Environment.getInstance();
        EventManagerOperatingMode operatingMode = EventManagerOperatingMode.fromEnvironment();
        String surfaceId = resolveRegistrySurfaceId(environment);
        LocalRegistrySnapshotStore snapshotStore = new LocalRegistrySnapshotStore(
                resolveRegistrySnapshotPath(environment, surfaceId));

        if (operatingMode.isLocal()) {
            boolean snapshotLoaded = loadLocalRegistrySnapshot(snapshotStore, surfaceId, "local registry mode");
            registryRuntimeStatus = localRegistryStatus(
                    operatingMode,
                    snapshotLoaded,
                    false,
                    true,
                    "Local registry mode does not use a database");
            return;
        }

        try {
            var config = MetaDbConfig.fromEnvironment(environment);
            if (config.isEmpty()) {
                boolean snapshotLoaded = loadLocalRegistrySnapshot(
                        snapshotStore,
                        surfaceId,
                        "MetaDB is not configured");
                boolean registryReady = operatingMode == EventManagerOperatingMode.AUTO;
                registryRuntimeStatus = localRegistryStatus(
                        operatingMode,
                        snapshotLoaded,
                        true,
                        registryReady,
                        registryReady
                                ? "Auto mode selected the local registry because MetaDB is not configured"
                                : "Database mode requires complete MetaDB configuration");
                return;
            }

            MetaDbConfig metaDb = config.get();
            try (Connection metaConn = SimpleConnection.getConnection(
                metaDb.host(),
                metaDb.port(),
                metaDb.database(),
                metaDb.username(),
                metaDb.password(),
                metaDb.ssl(),
                metaDb.allowRemotePlaintext()
            )) {
                Collection<ServiceCallDefinition> discoveredDefinitions = pluginManager.getDiscoveredDefinitions();
                boolean refreshCompleted = refreshDiscoveredDefinitions(metaConn, discoveredDefinitions, surfaceId);
                var loadedDefinitions = DbServiceRegistryLoader.loadAllFromMetaDb(metaConn, surfaceId);

                registerLoadedDefinitions(
                        loadedDefinitions,
                        FunctionRegistrationOrigin.SERVER_REGISTRY);
                saveLocalRegistrySnapshot(snapshotStore, surfaceId, loadedDefinitions);
                registryRuntimeStatus = new RegistryRuntimeStatus(
                        operatingMode,
                        RegistryRuntimeStatus.Source.DATABASE,
                        refreshCompleted,
                        false,
                        registry.listServiceTypes().size(),
                        refreshCompleted
                                ? "Database registry reconciliation and load completed"
                                : "Database registry loaded, but surface reconciliation failed");
            }
        } catch (Exception e) {
            System.err.println("Warning: Registry database unavailable; continuing with the local registry. Cause: "
                    + safeMessage(e));
            boolean snapshotLoaded = loadLocalRegistrySnapshot(
                    snapshotStore,
                    surfaceId,
                    "registry database is unavailable");
            registryRuntimeStatus = localRegistryStatus(
                    operatingMode,
                    snapshotLoaded,
                    true,
                    operatingMode == EventManagerOperatingMode.AUTO,
                    operatingMode == EventManagerOperatingMode.AUTO
                            ? "Auto mode fell back after the registry database was unavailable"
                            : "Database mode fell back locally because the registry database was unavailable");
        }
    }

    private RegistryRuntimeStatus localRegistryStatus(EventManagerOperatingMode operatingMode,
                                                       boolean snapshotLoaded,
                                                       boolean fallback,
                                                       boolean ready,
                                                       String detail) {
        RegistryRuntimeStatus.Source source = snapshotLoaded
                ? RegistryRuntimeStatus.Source.LOCAL_SNAPSHOT
                : RegistryRuntimeStatus.Source.DISCOVERED_LOCAL;
        return new RegistryRuntimeStatus(
                operatingMode,
                source,
                ready,
                fallback,
                registry.listServiceTypes().size(),
                detail);
    }

    private void registerLoadedDefinitions(
            Collection<ServiceCallDefinition> loadedDefinitions,
            FunctionRegistrationOrigin origin) {
        GenericProcessHandler genericHandler = new GenericProcessHandler(this);
        Set<String> loadedServiceTypes = new LinkedHashSet<>();
        for (ServiceCallDefinition definition : loadedDefinitions) {
            if (definition == null
                    || definition.getServiceType() == null
                    || definition.getServiceType().isBlank()) {
                continue;
            }
            registerDatabaseRegistryDefinition(definition, genericHandler, origin);
            loadedServiceTypes.add(definition.getServiceType());
        }
        if (!loadedServiceTypes.isEmpty()) {
            System.out.printf("[Engine] Loaded %d definitions from the registry%n", loadedServiceTypes.size());
        }
    }

    private boolean loadLocalRegistrySnapshot(LocalRegistrySnapshotStore snapshotStore,
                                              String surfaceId,
                                              String reason) {
        try {
            var snapshot = snapshotStore.load(surfaceId);
            if (snapshot.isEmpty()) {
                System.out.printf("[Engine] No local registry snapshot at %s; keeping discovered local definitions (%s)%n",
                        snapshotStore.path(), reason);
                return false;
            }

            registerLoadedDefinitions(
                    snapshot.get(),
                    FunctionRegistrationOrigin.LOCAL_SNAPSHOT);
            System.out.printf("[Engine] Loaded local registry snapshot from %s (%s)%n",
                    snapshotStore.path(), reason);
            return true;
        } catch (Exception snapshotError) {
            System.err.printf("Warning: Local registry snapshot is unavailable at %s; keeping discovered local definitions. Cause: %s%n",
                    snapshotStore.path(), safeMessage(snapshotError));
            return false;
        }
    }

    private void saveLocalRegistrySnapshot(LocalRegistrySnapshotStore snapshotStore,
                                           String surfaceId,
                                           List<ServiceCallDefinition> definitions) {
        try {
            snapshotStore.save(surfaceId, definitions);
            System.out.printf("[Engine] Saved local registry snapshot to %s%n", snapshotStore.path());
        } catch (Exception snapshotError) {
            System.err.printf("Warning: Could not save local registry snapshot to %s: %s%n",
                    snapshotStore.path(), safeMessage(snapshotError));
        }
    }

    private Path resolveRegistrySnapshotPath(Environment environment, String surfaceId) {
        String configured = environment.get(LocalRegistrySnapshotStore.SNAPSHOT_PATH_CONFIG, null);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }

        Path pluginPath = pluginRoot.toPath().toAbsolutePath().normalize();
        Path parent = pluginPath.getParent();
        Path runtimeRoot = parent == null ? Path.of(".everyarrow") : parent.resolve(".everyarrow");
        return runtimeRoot.resolve("registry").resolve(fileSafeSurfaceId(surfaceId) + ".json");
    }

    private String fileSafeSurfaceId(String surfaceId) {
        String normalized = surfaceId == null || surfaceId.isBlank() ? "default" : surfaceId.trim();
        return normalized.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String safeMessage(Exception error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private boolean refreshDiscoveredDefinitions(Connection metaConn,
                                                 Collection<ServiceCallDefinition> discoveredDefinitions,
                                                 String surfaceId) {
        try {
            int refreshed = new DbServiceRegistryRefresher().refresh(
                    metaConn,
                    discoveredDefinitions,
                    pluginManager.getAllLoadedClasses(),
                    pluginManager.getLoadedClassSources(),
                    surfaceId);
            if (refreshed > 0) {
                System.out.printf("[Engine] Refreshed %d discovered definitions/metadata into event_manager.registry_functions%n",
                        refreshed);
            }
            return true;
        } catch (Exception refreshError) {
            System.err.println("Warning: Failed to refresh discovered service registry metadata: "
                    + refreshError.getMessage());
            return false;
        }
    }

    private void registerDatabaseRegistryDefinition(ServiceCallDefinition def,
                                                     GenericProcessHandler genericHandler,
                                                     FunctionRegistrationOrigin origin) {
        if (isGenericProcessHandler(def)) {
            registry.register(def.getServiceType(), genericHandler, def, origin);
            return;
        }

        pluginManager.replaceDefinitionFromRegistry(def);
        registry.registerDefinition(def, origin);
    }

    private boolean isGenericProcessHandler(ServiceCallDefinition def) {
        String handlerClass = firstNonBlank(def.getHandlerClass(), def.getClassName());
        return GenericProcessHandler.class.getSimpleName().equals(handlerClass)
                || GenericProcessHandler.class.getName().equals(handlerClass)
                || (def.getSource() != null && def.getSource().startsWith("db:"));
    }

    private String resolveRegistrySurfaceId(Environment environment) {
        String explicit = firstNonBlank(
                environment.get("EA_EVENT_MANAGER_SURFACE_ID", null),
                firstNonBlank(
                        environment.get("EA_PLUGIN_SURFACE_ID", null),
                        environment.get("EA_EVENT_MANAGER_SURFACE", null)
                )
        );
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }

        String pluginRoot = environment.get("EA_PLUGIN_ROOT", null);
        if (pluginRoot != null && !pluginRoot.isBlank()) {
            return new File(pluginRoot).getName();
        }
        return "default";
    }

    private String firstNonBlank(String primary, String secondary) {
        return primary == null || primary.isBlank() ? secondary : primary;
    }

    /**
     * Start external adapters and mark engine as running.
     */
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Engine must be initialized before start()");
        }
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            running = true;
        }

        EnginePluginContextImpl pluginCtx = new EnginePluginContextImpl();
        pluginCtx.registerService(Engine.class, this);
        pluginCtx.registerService(ServiceRegistry.class, registry);
        pluginCtx.registerService(SecurityDecisionCache.class, securityDecisionCache);
        pluginCtx.registerService(SecurityProviderRegistry.class, securityProviderRegistry);
        pluginCtx.registerService(JsonServiceRegistrySyncService.class, jsonServiceRegistrySyncService);
        realtimeGateway = new SocketIoGatewayServlet(this, securityProviderRegistry);
        pluginCtx.registerService(RealtimeGateway.class, realtimeGateway);
        this.pluginContext = pluginCtx;

        pluginManager.preStartBackgroundPlugins(pluginCtx);
        pluginManager.runBackgroundHealthChecks(pluginCtx);

        // Start protocol adapters after platform hooks and plugin definitions are ready.
        ProtocolAdapterManager.startAll(this);

        pluginManager.startBackgroundPlugins(pluginCtx);
        pluginManager.startPluginWatcher(pluginCtx);
        pluginManager.printStartupHealthSummary();
    }

    public void run() {
        synchronized (lifecycleMonitor) {
            while (running) {
                try {
                    lifecycleMonitor.wait();
                } catch (InterruptedException ignored) {}
            }
        }
    }

    public void shutdown() {
        synchronized (lifecycleMonitor) {
            if (!running) {
                return;
            }
            running = false;
        }

        pluginManager.stopPluginWatcher();
        pluginManager.stopBackgroundPlugins();

        // Stop HTTP server if it was started (same shutdown path for prod + tests).
        try {
            JettyBootstrap.stop();
        } catch (Exception ignored) {
        } finally {
            SocketIoGatewayServlet gateway = realtimeGateway;
            realtimeGateway = null;
            if (gateway != null) {
                gateway.shutdown();
            }
        }

        ProtocolAdapterManager.stopAll();

        synchronized (lifecycleMonitor) {
            lifecycleMonitor.notifyAll();
        }

        System.out.println("Engine shutdown completed.");
    }

    // ---------------------------------------------------------------------
    // Execution pipeline
    // ---------------------------------------------------------------------

    public ServiceCallResponse execute(ServiceCall call) throws Exception {
        return serviceCallPipeline.execute(call);
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    public ServiceRegistry getRegistry() {
        return registry;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public EnginePluginContext getPluginContext() {
        return pluginContext;
    }

    SocketIoGatewayServlet getRealtimeGateway() {
        return realtimeGateway;
    }

    public JsonServiceRegistrySyncService getJsonServiceRegistrySyncService() {
        return jsonServiceRegistrySyncService;
    }

    public SecurityDecisionCache getSecurityDecisionCache() {
        return securityDecisionCache;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public RegistryRuntimeStatus getRegistryRuntimeStatus() {
        return registryRuntimeStatus;
    }

    public boolean isReady() {
        return initialized
                && running
                && registryRuntimeStatus.ready()
                && pluginManager.areRequiredPlatformPluginsReady();
    }
}
