/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of plugin/core-discovered handlers and their metadata.
 *
 * Responsibilities:
 * - serviceType -> ServiceCallDefinition
 * - serviceType -> handler class name
 * - className   -> class loader
 * - className   -> loaded Class
 *
 * This keeps PluginManager focused on orchestration rather than storage.
 */
public class PluginIndex {

    private final Map<String, ServiceCallDefinition> defIndex = new ConcurrentHashMap<>();
    private final Map<String, String> classIndex = new ConcurrentHashMap<>();
    private final Map<String, ClassLoader> classLoaderIndex = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();
    private final Map<String, String> classSourceIndex = new ConcurrentHashMap<>();

    public void putDefinitionIfAbsent(String serviceType, ServiceCallDefinition definition) {
        defIndex.putIfAbsent(serviceType, definition);
    }

    public void putDefinition(String serviceType, ServiceCallDefinition definition) {
        if (serviceType == null || serviceType.isBlank() || definition == null) {
            return;
        }
        defIndex.put(serviceType, definition);
    }

    public void putHandlerClassNameIfAbsent(String serviceType, String className) {
        classIndex.putIfAbsent(serviceType, className);
    }

    public void putHandlerClassName(String serviceType, String className) {
        if (serviceType == null || serviceType.isBlank() || className == null || className.isBlank()) {
            return;
        }
        classIndex.put(serviceType, className);
    }

    public void putClassLoaderIfAbsent(String className, ClassLoader classLoader) {
        classLoaderIndex.putIfAbsent(className, classLoader);
    }

    public void putLoadedClass(String className, Class<?> clazz) {
        loadedClasses.put(className, clazz);
    }

    public void putLoadedClassIfAbsent(String className, Class<?> clazz) {
        loadedClasses.putIfAbsent(className, clazz);
    }

    public void putClassSourceIfAbsent(String className, String source) {
        if (className == null || className.isBlank() || source == null || source.isBlank()) {
            return;
        }
        classSourceIndex.putIfAbsent(className, source);
    }

    public Optional<ServiceCallDefinition> getDefinition(String serviceType) {
        return Optional.ofNullable(defIndex.get(serviceType));
    }

    public Optional<String> getHandlerClassNameForService(String serviceType) {
        return Optional.ofNullable(classIndex.get(serviceType));
    }

    public Optional<ClassLoader> getClassLoaderFor(String className) {
        return Optional.ofNullable(classLoaderIndex.get(className));
    }

    public Optional<Class<?>> findClass(String className) {
        return Optional.ofNullable(loadedClasses.get(className));
    }

    public Collection<Class<?>> getAllLoadedClasses() {
        return Collections.unmodifiableCollection(loadedClasses.values());
    }

    public Map<String, String> getClassSources() {
        return Collections.unmodifiableMap(classSourceIndex);
    }

    public Collection<ServiceCallDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(defIndex.values());
    }

    public int getLoadedClassCount() {
        return loadedClasses.size();
    }

    public void removeServiceType(String serviceType) {
        defIndex.remove(serviceType);
        classIndex.remove(serviceType);
    }

    public void clear() {
        defIndex.clear();
        classIndex.clear();
        classLoaderIndex.clear();
        loadedClasses.clear();
        classSourceIndex.clear();
    }
}
