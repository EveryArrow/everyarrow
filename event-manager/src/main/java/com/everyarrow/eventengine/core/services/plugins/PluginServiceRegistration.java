/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.AnnotationDefinitionBuilder;
import com.everyarrow.eventengine.core.services.registry.DbServiceRegistrationPolicy;
import com.everyarrow.eventengine.core.services.registry.FunctionRegistrationOrigin;
import com.everyarrow.eventengine.core.services.registry.JsonServiceRegistryLoader;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Registers service definitions discovered from core classes and external plugins.
 */
final class PluginServiceRegistration {

    private final ServiceRegistry registry;
    private final PluginIndex pluginIndex;
    private Engine engine;

    PluginServiceRegistration(ServiceRegistry registry, PluginIndex pluginIndex) {
        this.registry = registry;
        this.pluginIndex = pluginIndex;
    }

    void setEngine(Engine engine) {
        this.engine = engine;
    }

    int registerExternalPlugin(LoadedPlugin loadedPlugin) {
        String pluginId = loadedPlugin.getEffectivePluginId();
        String source = "plugin:" + pluginId;
        PluginConflictTracker conflictTracker = new PluginConflictTracker(pluginId);

        int externalRegistryDefinitionCount = 0;
        if (loadedPlugin.getRegistryDocument().isPresent()) {
            indexPluginRegistryDocument(loadedPlugin, conflictTracker);
        }

        for (Class<?> handlerClass : loadedPlugin.getDiscoveredHandlerClasses()) {
            handleDiscoveredHandler(
                    handlerClass,
                    loadedPlugin.getClassLoader(),
                    source,
                    conflictTracker,
                    loadedPlugin
            );
        }

        // Concrete Java handlers must claim their service types before DB-function
        // registry files add generic fallbacks for the same names. These local
        // definitions also become the restart reconciliation input.
        externalRegistryDefinitionCount += loadPluginServiceRegistries(loadedPlugin, conflictTracker);
        return externalRegistryDefinitionCount;
    }

    /**
     * Loads all per-plugin JSON service registries discovered for the plugin. Entries describe
     * DB-function-backed services that are routed through GenericProcessHandler and projected
     * to the server registry during restart reconciliation.
     */
    private int loadPluginServiceRegistries(LoadedPlugin loadedPlugin,
                                            PluginConflictTracker conflictTracker) {
        String pluginId = loadedPlugin.getEffectivePluginId();
        int totalRegistered = 0;

        for (PluginServiceRegistrySource registrySource : loadedPlugin.getServiceRegistrySources()) {
            List<ServiceCallDefinition> definitions = loadPluginServiceRegistryDefinitions(loadedPlugin, registrySource);
            if (definitions.isEmpty() && registrySource.declared()) {
                throw new IllegalStateException("Declared plugin registry resource did not define any services: "
                        + registrySource.describe());
            }

            int registered = 0;
            for (ServiceCallDefinition definition : definitions) {
                applyPluginOwnership(definition, loadedPlugin);
                if (isBlank(definition.getSource())) {
                    definition.setSource("db:" + firstNonBlank(
                            definition.getDbDatabase(),
                            "unknown"));
                }
                if (!conflictTracker.record(definition, registrySource.describe())) {
                    continue;
                }
                warnOnCrossPluginConflicts(definition, registrySource.describe());
                if (registerGenericFunction(definition, pluginId)) {
                    loadedPlugin.addRegisteredServiceType(definition.getServiceType());
                    registered++;
                }
            }
            totalRegistered += registered;
        }
        return totalRegistered;
    }

    private List<ServiceCallDefinition> loadPluginServiceRegistryDefinitions(
            LoadedPlugin loadedPlugin,
            PluginServiceRegistrySource registrySource) {
        if (registrySource.isClasspathResource()) {
            return JsonServiceRegistryLoader.loadRequiredFromClassLoader(
                    loadedPlugin.getClassLoader(),
                    registrySource.location());
        }
        return JsonServiceRegistryLoader.loadRequiredFromFile(registrySource.location());
    }

    private void indexPluginRegistryDocument(LoadedPlugin loadedPlugin, PluginConflictTracker conflictTracker) {
        PluginRegistryDocument document = loadedPlugin.getRegistryDocument().orElseThrow();
        String source = "plugin:" + loadedPlugin.getEffectivePluginId();
        String origin = "plugin manifest for " + loadedPlugin.getEffectivePluginId();

        for (ServiceCallDefinition def : document.getServices()) {
            if (def == null) {
                continue;
            }

            applyPluginOwnership(def, loadedPlugin);
            def.setActive(true);

            if (isBlank(def.getSource())) {
                def.setSource(isManifestGenericFunction(def)
                        ? "db:" + firstNonBlank(def.getDbDatabase(), "unknown")
                        : source);
            }

            if (!conflictTracker.record(def, origin)) {
                continue;
            }
            warnOnCrossPluginConflicts(def, origin);

            if (isManifestGenericFunction(def)) {
                if (registerGenericFunction(def, loadedPlugin.getEffectivePluginId())) {
                    loadedPlugin.addRegisteredServiceType(def.getServiceType());
                }
                continue;
            }

            String handlerClassName = def.getHandlerClass();
            if (isBlank(handlerClassName)) {
                handlerClassName = def.getClassName();
                def.setHandlerClass(handlerClassName);
            }

            registry.registerDefinition(
                    def,
                    FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                    loadedPlugin.getEffectivePluginId());
            loadedPlugin.addRegisteredServiceType(def.getServiceType());

            try {
                Class<?> clazz = loadedPlugin.getClassLoader().loadClass(handlerClassName);

                indexPreferredHandlerDefinition(def, handlerClassName);
                pluginIndex.putClassLoaderIfAbsent(handlerClassName, loadedPlugin.getClassLoader());
                pluginIndex.putLoadedClassIfAbsent(handlerClassName, clazz);
                pluginIndex.putClassSourceIfAbsent(handlerClassName, source);

            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Failed to load declared handler class " + handlerClassName
                        + " for plugin " + document.getPlugin().getId(), e);
            }
        }
    }

    private boolean registerGenericFunction(ServiceCallDefinition def,
                                            String pluginId) {
        def.setHandlerClass(GenericProcessHandler.class.getSimpleName());
        DbServiceRegistrationPolicy.validate(def, "plugin registry");
        registry.register(
                def.getServiceType(),
                createGenericProcessHandler(),
                def,
                FunctionRegistrationOrigin.LOCAL_REGISTRY,
                pluginId);

        pluginIndex.putDefinitionIfAbsent(def.getServiceType(), def);
        pluginIndex.putHandlerClassNameIfAbsent(def.getServiceType(), GenericProcessHandler.class.getName());
        pluginIndex.putClassLoaderIfAbsent(GenericProcessHandler.class.getName(), GenericProcessHandler.class.getClassLoader());
        pluginIndex.putLoadedClassIfAbsent(GenericProcessHandler.class.getName(), GenericProcessHandler.class);

        return true;
    }

    private ServiceCallHandler createGenericProcessHandler() {
        if (engine != null) {
            return new GenericProcessHandler(engine);
        }

        return new GenericProcessHandler();
    }

    private boolean isManifestGenericFunction(ServiceCallDefinition def) {
        String handlerClassName = firstNonBlank(def.getHandlerClass(), def.getClassName());

        if (isGenericProcessHandlerName(handlerClassName)) {
            return isDbBackedService(def);
        }

        return isBlank(handlerClassName) && isDbBackedService(def);
    }

    private boolean isGenericProcessHandlerName(String handlerClassName) {
        return GenericProcessHandler.class.getSimpleName().equals(handlerClassName)
                || GenericProcessHandler.class.getName().equals(handlerClassName);
    }

    private boolean isDbBackedService(ServiceCallDefinition def) {
        if (def.getSource() != null && def.getSource().startsWith("db:")) {
            return true;
        }

        String serviceType = def.getServiceType();
        return !isBlank(serviceType) && serviceType.split("\\.").length == 3;
    }

    private String firstNonBlank(String primary, String secondary) {
        if (!isBlank(primary)) {
            return primary;
        }
        return secondary;
    }

    void scanLocalServices() {
        List<Path> candidateRoots = List.of(
                Paths.get("target", "classes"),
                Paths.get("build", "classes", "java", "main")
        );

        String servicesPackagePath = "com/everyarrow/eventengine/core/services";

        for (Path root : candidateRoots) {
            Path servicesRoot = root.resolve(servicesPackagePath);
            if (!Files.exists(servicesRoot)) {
                continue;
            }

            try {
                Files.walk(servicesRoot)
                        .filter(p -> p.toString().endsWith(".class"))
                        .forEach(p -> {
                            try {
                                String rel = root.relativize(p).toString();
                                String className = rel
                                        .replace(File.separatorChar, '.')
                                        .replaceAll("\\.class$", "");
                                registerLocalClassForIndex(className);
                            } catch (Throwable t) {
                                System.err.printf("Warning: Skipping local class %s: %s%n", p, t.getMessage());
                            }
                        });
            } catch (IOException ex) {
                System.err.printf("Warning: Error scanning %s: %s%n", servicesRoot, ex.getMessage());
            }
        }

        scanPackagedCoreServices(servicesPackagePath);
    }

    private void scanPackagedCoreServices(String servicesPackagePath) {
        try {
            CodeSource codeSource = PluginManager.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return;
            }

            URL location = codeSource.getLocation();
            if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
                return;
            }

            Path classpathLocation = Paths.get(location.toURI());
            if (!Files.isRegularFile(classpathLocation) || !classpathLocation.toString().endsWith(".jar")) {
                return;
            }

            try (JarFile jar = new JarFile(classpathLocation.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory()
                            || !name.startsWith(servicesPackagePath)
                            || !name.endsWith(".class")) {
                        continue;
                    }

                    String className = name
                            .replace('/', '.')
                            .replaceAll("\\.class$", "");
                    registerLocalClassForIndex(className);
                }
            }
        } catch (Exception ex) {
            System.err.printf("Warning: Error scanning packaged core services: %s%n", ex.getMessage());
        }
    }

    private void registerLocalClassForIndex(String className) {
        try {
            ClassLoader cl = this.getClass().getClassLoader();
            Class<?> clazz = cl.loadClass(className);

            int mods = clazz.getModifiers();
            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(mods) || clazz.isEnum()) {
                return;
            }

            if (ServiceCallHandler.class.isAssignableFrom(clazz)) {
                pluginIndex.putLoadedClass(className, clazz);
                pluginIndex.putClassSourceIfAbsent(className, "core");
                indexHandler(clazz, cl, "core");
            }

        } catch (Throwable t) {
            System.err.printf("Warning: Could not register local class %s: %s%n", className, t.getMessage());
        }
    }

    private void handleDiscoveredHandler(
            Class<?> clazz,
            ClassLoader loader,
            String source,
            PluginConflictTracker conflictTracker,
            LoadedPlugin loadedPlugin) {
        pluginIndex.putLoadedClass(clazz.getName(), clazz);
        pluginIndex.putClassSourceIfAbsent(clazz.getName(), source);
        indexHandler(clazz, loader, source, conflictTracker, loadedPlugin);
    }

    private void indexHandler(Class<?> clazz, ClassLoader loader, String source) {
        indexHandler(clazz, loader, source, null, null);
    }

    private void indexHandler(
            Class<?> clazz,
            ClassLoader loader,
            String source,
            PluginConflictTracker conflictTracker,
            LoadedPlugin loadedPlugin) {
        try {
            List<ServiceCallDefinition> defs = AnnotationDefinitionBuilder.buildDefinitions(clazz);

            if (!defs.isEmpty()) {
                for (ServiceCallDefinition def : defs) {
                    def.setHandlerClass(clazz.getName());
                    def.setSource(source);
                    def.setActive(true);
                    if (loadedPlugin != null) {
                        applyPluginOwnership(def, loadedPlugin);
                    }

                    if (conflictTracker != null) {
                        String origin = handlerOrigin(clazz, def);
                        if (!conflictTracker.record(def, origin)) {
                            continue;
                        }
                        warnOnCrossPluginConflicts(def, origin);
                    }

                    boolean hasRest = def.hasRestRoute();
                    boolean hasWebhook = def.hasWebhook();
                    boolean hasWebSocket = def.hasWebSocketRoute();
                    boolean hasCalendar = def.getAnnotations() != null
                            && def.getAnnotations().calendarJobs != null
                            && !def.getAnnotations().calendarJobs.isEmpty();
                    boolean hasQueue = def.hasQueueSubscriptions();
                    boolean hasPostgresNotification = def.hasPostgresNotifications();

                    if (loadedPlugin != null) {
                        registry.registerDefinition(
                                def,
                                FunctionRegistrationOrigin.LOCAL_ANNOTATION,
                                loadedPlugin.getEffectivePluginId());
                        loadedPlugin.addRegisteredServiceType(def.getServiceType());
                    } else if (hasRest || hasWebhook || hasWebSocket || hasCalendar
                            || hasQueue || hasPostgresNotification) {
                        registry.registerDefinition(def);
                    }

                    String type = def.getServiceType();
                    indexPreferredHandlerDefinition(def, clazz.getName());
                    pluginIndex.putClassLoaderIfAbsent(clazz.getName(), loader);
                    pluginIndex.putLoadedClassIfAbsent(clazz.getName(), clazz);
                    pluginIndex.putClassSourceIfAbsent(clazz.getName(), source);
                }
            } else {
                // No annotations found - register with derived service type for index only.
                ServiceCallDefinition def = new ServiceCallDefinition();
                def.setServiceType(extractServiceType(clazz.getSimpleName()));
                def.setHandlerClass(clazz.getName());
                def.setSource(source);
                def.setActive(true);

                if (conflictTracker != null) {
                    String origin = "annotated handler " + clazz.getName();
                    if (!conflictTracker.record(def, origin)) {
                        return;
                    }
                    warnOnCrossPluginConflicts(def, origin);
                }

                String type = def.getServiceType();
                if (loadedPlugin != null) {
                    loadedPlugin.addRegisteredServiceType(type);
                }
                indexPreferredHandlerDefinition(def, clazz.getName());
                pluginIndex.putClassLoaderIfAbsent(clazz.getName(), loader);
                pluginIndex.putLoadedClassIfAbsent(clazz.getName(), clazz);
                pluginIndex.putClassSourceIfAbsent(clazz.getName(), source);
            }

        } catch (Throwable t) {
            // Same-plugin duplicate: manifest registered the serviceType first,
            // annotation scan found the same type. Merge annotation metadata
            // (queue subscriptions, calendar jobs, etc.) into the existing definition
            // so protocol adapters can still discover them.
            List<ServiceCallDefinition> annotatedDefs = null;
            try {
                annotatedDefs = AnnotationDefinitionBuilder.buildDefinitions(clazz);
            } catch (Throwable ignored) {
            }

            if (annotatedDefs != null) {
                for (ServiceCallDefinition annotated : annotatedDefs) {
                    Optional<ServiceCallDefinition> existing =
                            pluginIndex.getDefinition(annotated.getServiceType());
                    // PluginIndex and ServiceRegistry retain the same definition instance.
                    // Merge protocol metadata in place; re-registering the mutated definition
                    // would create a second candidate with a different signature.
                    if (existing.isPresent() && annotated.hasQueueSubscriptions()) {
                        for (ServiceCallDefinition.QueueSubscriptionMeta sub
                                : annotated.getAnnotations().queueSubscriptions) {
                            existing.get().addQueueSubscription(sub);
                        }
                    }
                    if (existing.isPresent() && annotated.hasPostgresNotifications()) {
                        for (ServiceCallDefinition.PostgresNotificationMeta notification
                                : annotated.getAnnotations().postgresNotifications) {
                            existing.get().addPostgresNotification(notification);
                        }
                    }
                }
            }

            if (annotatedDefs == null || annotatedDefs.isEmpty()) {
                System.err.printf("Warning: Failed to index handler %s: %s%n", clazz.getName(), t.getMessage());
            }
        }
    }

    private void indexPreferredHandlerDefinition(ServiceCallDefinition definition,
                                                 String handlerClassName) {
        String serviceType = definition.getServiceType();
        Optional<String> existingHandler =
                pluginIndex.getHandlerClassNameForService(serviceType);
        if (existingHandler.isEmpty()
                || isGenericProcessHandlerName(existingHandler.get())) {
            pluginIndex.putDefinition(serviceType, definition);
            pluginIndex.putHandlerClassName(serviceType, handlerClassName);
            return;
        }
        pluginIndex.putDefinitionIfAbsent(serviceType, definition);
        pluginIndex.putHandlerClassNameIfAbsent(serviceType, handlerClassName);
    }

    private void applyPluginOwnership(
            ServiceCallDefinition definition,
            LoadedPlugin loadedPlugin) {
        if (definition == null
                || definition.getAnnotations() == null
                || definition.getAnnotations().functionContract == null) {
            return;
        }
        ServiceCallDefinition.FunctionContractMeta contract =
                definition.getAnnotations().functionContract;
        String pluginId = loadedPlugin.getEffectivePluginId();
        String pluginVersion = loadedPlugin.getEffectivePluginVersion();

        if (!isBlank(contract.pluginId) && !pluginId.equals(contract.pluginId)) {
            throw new IllegalStateException(
                    "Function contract pluginId '" + contract.pluginId
                            + "' does not match owning plugin '" + pluginId + "'");
        }
        if (!isBlank(contract.pluginVersion)
                && !java.util.Objects.equals(pluginVersion, contract.pluginVersion)) {
            throw new IllegalStateException(
                    "Function contract pluginVersion '" + contract.pluginVersion
                            + "' does not match owning plugin version '"
                            + pluginVersion + "'");
        }
        contract.pluginId = pluginId;
        contract.pluginVersion = pluginVersion;
    }

    private String extractServiceType(String className) {
        String base = className.replace("Handler", "");
        if (base.isEmpty()) {
            return "unknown";
        }
        return Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }

    private String handlerOrigin(Class<?> clazz, ServiceCallDefinition def) {
        if (def.hasTargetMethod()) {
            return "annotated handler " + clazz.getName() + "#" + def.getTargetMethodName();
        }
        return "annotated handler " + clazz.getName();
    }

    private void warnOnCrossPluginConflicts(ServiceCallDefinition def, String origin) {
        String serviceType = def.getServiceType();
        if (!isBlank(serviceType)) {
            Optional<ServiceCallDefinition> existing = pluginIndex.getDefinition(serviceType);
            if (existing.isPresent()) {
                validateFunctionConflict(existing.get(), def, origin);
            }
        }

        if (def.hasRestRoute()) {
            var rr = def.getRestRoute();
            ServiceCallDefinition existing = registry.findRestRoute(rr.method, rr.path);
            if (existing != null && !existing.getServiceType().equals(def.getServiceType())) {
                System.err.printf(
                        "Warning: Cross-plugin duplicate REST route %s %s from %s conflicts with %s (%s). Keeping first-loaded route.%n",
                        rr.method,
                        rr.path,
                        origin,
                        existing.getServiceType(),
                        existing.getSource()
                );
            }
        }

        if (def.hasWebhook()) {
            var wh = def.getWebhook();
            ServiceCallDefinition existing = registry.findWebhook(wh.path);
            if (existing != null && !existing.getServiceType().equals(def.getServiceType())) {
                System.err.printf(
                        "Warning: Cross-plugin duplicate webhook path %s from %s conflicts with %s (%s). Keeping first-loaded route.%n",
                        wh.path,
                        origin,
                        existing.getServiceType(),
                        existing.getSource()
                );
            }
        }

        if (def.hasWebSocketRoute()) {
            var ws = def.getWebSocketRoute();
            ServiceCallDefinition existing = registry.findWebSocket(ws.path);
            if (existing != null && !existing.getServiceType().equals(def.getServiceType())) {
                System.err.printf(
                        "Warning: Cross-plugin duplicate websocket path %s from %s conflicts with %s (%s). Keeping first-loaded route.%n",
                        ws.path,
                        origin,
                        existing.getServiceType(),
                        existing.getSource()
                );
            }
        }
    }

    private void validateFunctionConflict(ServiceCallDefinition existing,
                                          ServiceCallDefinition candidate,
                                          String origin) {
        boolean existingGeneric = isGenericProcessHandlerName(firstNonBlank(
                existing.getHandlerClass(),
                existing.getClassName()));
        boolean candidateGeneric = isGenericProcessHandlerName(firstNonBlank(
                candidate.getHandlerClass(),
                candidate.getClassName()));
        if (existingGeneric != candidateGeneric) {
            return;
        }

        boolean sameHandler = java.util.Objects.equals(
                firstNonBlank(existing.getHandlerClass(), existing.getClassName()),
                firstNonBlank(candidate.getHandlerClass(), candidate.getClassName()));
        boolean sameTarget = java.util.Objects.equals(
                existing.getTargetMethodName(),
                candidate.getTargetMethodName());
        boolean sameDatabaseFunction = java.util.Objects.equals(
                existing.getDbDatabase(),
                candidate.getDbDatabase())
                && java.util.Objects.equals(existing.getDbSchema(), candidate.getDbSchema())
                && java.util.Objects.equals(existing.getDbFunction(), candidate.getDbFunction());
        boolean sameSource = java.util.Objects.equals(
                existing.getSource(),
                candidate.getSource());

        if (sameSource
                && ((existingGeneric && sameDatabaseFunction)
                || (!existingGeneric && sameHandler && sameTarget))) {
            return;
        }

        throw new IllegalStateException(
                "Ambiguous cross-plugin serviceType '" + candidate.getServiceType()
                        + "' from " + origin
                        + " conflicts with existing source " + existing.getSource()
                        + ". Use distinct serviceType namespaces or an explicit override relationship.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class PluginConflictTracker {

        private final String pluginId;
        private final Map<String, TrackedDeclaration> serviceTypes = new LinkedHashMap<>();
        private final Map<String, TrackedDeclaration> routes = new LinkedHashMap<>();

        private PluginConflictTracker(String pluginId) {
            this.pluginId = pluginId;
        }

        private boolean record(ServiceCallDefinition def, String origin) {
            if (def == null) {
                return true;
            }

            if (!recordUnique(serviceTypes, "serviceType", def.getServiceType(), def, origin)) {
                return false;
            }

            if (def.hasRestRoute()) {
                var rr = def.getRestRoute();
                String method = isBlank(rr.method) ? "GET" : rr.method.toUpperCase();
                if (!recordUnique(routes, "REST route", method + " " + rr.path, def, origin)) {
                    return false;
                }
            }

            if (def.hasWebhook()) {
                if (!recordUnique(routes, "Webhook route", def.getWebhook().path, def, origin)) {
                    return false;
                }
            }

            if (def.hasWebSocketRoute()) {
                if (!recordUnique(routes, "WebSocket route", def.getWebSocketRoute().path, def, origin)) {
                    return false;
                }
            }

            return true;
        }

        private boolean recordUnique(
                Map<String, TrackedDeclaration> index,
                String label,
                String key,
                ServiceCallDefinition def,
                String origin) {
            if (isBlank(key)) {
                return true;
            }

            TrackedDeclaration declaration = new TrackedDeclaration(origin, DefinitionSignature.from(def));
            TrackedDeclaration existing = index.putIfAbsent(key, declaration);
            if (existing == null) {
                return true;
            }

            if (existing.signature().equals(declaration.signature())) {
                System.err.printf(
                        "Info: Plugin '%s' declares equivalent %s '%s' in both %s and %s. Treating the duplicate as idempotent.%n",
                        pluginId,
                        label,
                        key,
                        existing.origin(),
                        origin
                );
                return false;
            }

            if (java.util.Objects.equals(
                    existing.signature().serviceType(),
                    declaration.signature().serviceType())
                    && isGenericAndConcretePair(existing.signature(), declaration.signature())) {
                System.err.printf(
                        "Info: Plugin '%s' declares %s '%s' as both a concrete handler and a generic DB function in %s and %s. The concrete handler has deterministic precedence.%n",
                        pluginId,
                        label,
                        key,
                        existing.origin(),
                        origin
                );
                if (isGenericProcessHandler(existing.signature().handlerClass())
                        && !isGenericProcessHandler(declaration.signature().handlerClass())) {
                    index.put(key, declaration);
                }
                return true;
            }

            throw new IllegalStateException(
                    "Plugin '" + pluginId + "' declares duplicate " + label + " '" + key
                            + "' in both " + existing.origin() + " and " + origin
            );
        }

        private boolean isGenericAndConcretePair(DefinitionSignature left, DefinitionSignature right) {
            return isGenericProcessHandler(left.handlerClass()) != isGenericProcessHandler(right.handlerClass());
        }

        private boolean isGenericProcessHandler(String handlerClass) {
            return "GenericProcessHandler".equals(handlerClass)
                    || GenericProcessHandler.class.getName().equals(handlerClass);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        private record TrackedDeclaration(String origin, DefinitionSignature signature) {
        }

        private record DefinitionSignature(
                String serviceType,
                String handlerClass,
                String className,
                String category,
                String source,
                String jsonType,
                String restMethod,
                String restPath,
                String webhookPath,
                String webSocketPath) {

            private static DefinitionSignature from(ServiceCallDefinition def) {
                ServiceCallDefinition.RestRouteMeta restRoute = def.getRestRoute();
                ServiceCallDefinition.WebhookMeta webhook = def.getWebhook();
                ServiceCallDefinition.WebSocketRouteMeta webSocketRoute = def.getWebSocketRoute();
                return new DefinitionSignature(
                        def.getServiceType(),
                        def.getHandlerClass(),
                        def.getClassName(),
                        def.getCategory(),
                        def.getSource(),
                        def.getJsonType(),
                        restRoute == null ? null : normalizeMethod(restRoute.method),
                        restRoute == null ? null : restRoute.path,
                        webhook == null ? null : webhook.path,
                        webSocketRoute == null ? null : webSocketRoute.path
                );
            }

            private static String normalizeMethod(String method) {
                return method == null || method.isBlank() ? "GET" : method.toUpperCase();
            }
        }
    }
}
