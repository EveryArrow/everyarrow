/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.services.JsonServiceRegistrySyncService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime implementation for targeted JSON registry reload.
 *
 * Database-schema discovery and code generation are intentionally outside the
 * Event Manager runtime. This implementation only reloads supplied JSON files.
 */
public class JsonServiceRegistrySyncServiceImpl implements JsonServiceRegistrySyncService {

    private final Engine engine;
    private final ServiceRegistry registry;
    private final ConcurrentHashMap<String, ManagedRegistryState> managedRegistries = new ConcurrentHashMap<>();

    public JsonServiceRegistrySyncServiceImpl(Engine engine, ServiceRegistry registry) {
        this.engine = engine;
        this.registry = Objects.requireNonNull(registry, "registry is required");
    }

    @Override
    public ReloadResult reloadFile(Path targetPath) throws Exception {
        Objects.requireNonNull(targetPath, "targetPath is required");

        Path normalizedPath = normalizePath(targetPath);
        String registrySource = registrySource(normalizedPath);

        ManagedRegistryState previous = managedRegistries.remove(registrySource);
        if (previous != null) {
            registry.deregisterSource(registrySource);
        }

        List<ServiceCallDefinition> defs = JsonServiceRegistryLoader.loadFromFile(normalizedPath.toString());
        GenericProcessHandler handler = createGenericProcessHandler();
        Set<String> loadedTypes = new LinkedHashSet<>();

        for (ServiceCallDefinition def : defs) {
            if (def == null || isBlank(def.getServiceType())) {
                continue;
            }
            if (!loadedTypes.add(def.getServiceType())) {
                continue;
            }

            def.setSource(registrySource);
            def.setHandlerClass(GenericProcessHandler.class.getName());
            def.setCategory("Database");
            registry.register(def.getServiceType(), handler, def);
        }

        if (!loadedTypes.isEmpty()) {
            managedRegistries.put(registrySource, new ManagedRegistryState(normalizedPath, loadedTypes));
        }

        return new ReloadResult(normalizedPath, registrySource, loadedTypes.size());
    }

    @Override
    public List<ReloadResult> reloadManagedFiles() throws Exception {
        if (managedRegistries.isEmpty()) {
            return Collections.emptyList();
        }

        List<ManagedRegistryState> states = new ArrayList<>(managedRegistries.values());
        List<ReloadResult> results = new ArrayList<>(states.size());
        for (ManagedRegistryState state : states) {
            results.add(reloadFile(state.path()));
        }
        return results;
    }

    private GenericProcessHandler createGenericProcessHandler() {
        if (engine != null) {
            return new GenericProcessHandler(engine);
        }
        return new GenericProcessHandler();
    }

    private Path normalizePath(Path targetPath) {
        return targetPath.toAbsolutePath().normalize();
    }

    private String registrySource(Path targetPath) {
        return "runtime-registry:" + normalizePath(targetPath);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ManagedRegistryState(Path path, Set<String> serviceTypes) {
        private ManagedRegistryState {
            serviceTypes = Set.copyOf(serviceTypes);
        }
    }

}
