/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EveryArrow Environment Loader
 *
 * Responsibilities:
 * - Load env vars from:
 * * System.getenv()
 * * Multiple .env files (additive loading)
 * - Provide lazy-loaded lookup
 * - Delegate decryption to EnvironmentDecryptor
 * - Cache final resolved values
 *
 * Supports loading multiple component .env files:
 * Environment.loadComponent(".env.database");
 * Environment.loadComponent(".env.analytics");
 * // All values are merged into a single cache
 */
public final class Environment {

    /** Final decrypted results cache */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /** Raw un-decrypted environment variables (system + all loaded .env files) */
    private static final Map<String, String> RAW_ENV = new ConcurrentHashMap<>();

    /** Track which components have been loaded */
    private static final Set<String> LOADED_COMPONENTS = ConcurrentHashMap.newKeySet();

    /** Whether system environment has been loaded */
    private static volatile boolean systemEnvLoaded = false;

    private Environment() {
    }

    private static final class Holder {
        private static final Environment INSTANCE = new Environment();
    }

    public static Environment getInstance() {
        return Holder.INSTANCE;
    }

    // ---------------------------------------------------------------------
    // Initialization Methods
    // ---------------------------------------------------------------------

    /**
     * Initialize with a single .env file (legacy behavior - resets all).
     * For additive loading, use loadComponent() instead.
     */
    public static void initFromFile(Path file) {
        if (file == null) {
            throw new EnvironmentException("initFromFile: file path is null");
        }
        if (!Files.exists(file)) {
            throw new EnvironmentException("initFromFile: env file does not exist: " + file);
        }

        synchronized (Environment.class) {
            // Clear everything and start fresh
            RAW_ENV.clear();
            CACHE.clear();
            LOADED_COMPONENTS.clear();
            systemEnvLoaded = false;

            // Load system env + file
            ensureSystemEnvLoaded();
            Map<String, String> fileVars = loadFromFile(file);
            mergeNonSystemOverrides(fileVars);
            LOADED_COMPONENTS.add(file.toString());
        }
    }

    public static void initFromResource(String resourceName) {
        URL url = Environment.class.getClassLoader().getResource(resourceName);
        if (url != null) {
            synchronized (Environment.class) {
                // Clear everything and start fresh
                RAW_ENV.clear();
                CACHE.clear();
                LOADED_COMPONENTS.clear();
                systemEnvLoaded = false;

                // Load system env + file
                ensureSystemEnvLoaded();
                Map<String, String> vars = loadFromUrl(url, resourceName);
                mergeNonSystemOverrides(vars);
                LOADED_COMPONENTS.add(resourceName);
            }
        } else {
            System.err.println("⚠️  Component not found: " + resourceName);
        }
    }

    /**
     * Load a component .env file additively (merges with existing).
     * This is the preferred method for loading multiple .env files.
     * 
     * Example:
     * Environment.loadComponent(".env.database");
     * Environment.loadComponent(".env.analytics");
     * Environment.loadComponent(".env.notifications");
     */
    public static void loadComponent(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new EnvironmentException("loadComponent: resourceName is blank");
        }

        synchronized (Environment.class) {
            // Skip if already loaded
            if (LOADED_COMPONENTS.contains(resourceName)) {
                return;
            }

            // Ensure system env is loaded first
            ensureSystemEnvLoaded();

            // Load and merge the component
            URL url = Environment.class.getClassLoader().getResource(resourceName);
            if (url != null) {
                Map<String, String> vars = loadFromUrl(url, resourceName);
                mergeNonSystemOverrides(vars);
                LOADED_COMPONENTS.add(resourceName);
                System.out.println("✅ Loaded component: " + resourceName + " (" + vars.size() + " vars)");
            } else {
                System.err.println("⚠️  Component not found: " + resourceName);
            }
        }
    }

    /**
     * Load multiple component .env files at once.
     */
    public static void loadComponents(String... resourceNames) {
        for (String name : resourceNames) {
            loadComponent(name);
        }
    }

    // ---------------------------------------------------------------------
    // Public lookup API
    // ---------------------------------------------------------------------

    public String get(String key) {
        // Fast path - check decrypted cache
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        // Ensure system env is loaded
        ensureSystemEnvLoaded();

        String value = RAW_ENV.get(key);

        if (value == null) {
            return null;
        }

        // Decrypt if needed
        value = EnvironmentDecryptor.resolve(value);

        // Convert escaped newlines to real newlines (for multi-line values like private
        // keys)
        if (value != null && value.contains("\\n")) {
            value = value.replace("\\n", "\n");
        }

        CACHE.put(key, value);
        return value;
    }

    public boolean has(String key) {
        return get(key) != null;
    }

    public String get(String key, String defaultValue) {
        String v = get(key);
        return v != null ? v : defaultValue;
    }

    /**
     * Returns the names of environment values visible to this loader without exposing their values.
     *
     * @return immutable set of configured key names
     */
    public Set<String> keys() {
        ensureSystemEnvLoaded();
        return Set.copyOf(RAW_ENV.keySet());
    }

    public String require(String key) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            throw new EnvironmentException("Required environment variable missing: " + key);
        }
        return v;
    }

    public void verifyRequired(Collection<String> vars) {
        List<String> missing = new ArrayList<>();
        for (String var : vars) {
            if (!has(var)) {
                missing.add(var);
            }
        }
        if (!missing.isEmpty()) {
            throw new EnvironmentException(
                    "Missing environment variables: " + String.join(", ", missing));
        }
    }

    /**
     * Get list of loaded components.
     */
    public static Set<String> getLoadedComponents() {
        return Set.copyOf(LOADED_COMPONENTS);
    }

    /**
     * Check if a component has been loaded.
     */
    public static boolean isComponentLoaded(String resourceName) {
        return LOADED_COMPONENTS.contains(resourceName);
    }

    /**
     * Clear all loaded environment (for testing).
     */
    public static void reset() {
        synchronized (Environment.class) {
            RAW_ENV.clear();
            CACHE.clear();
            LOADED_COMPONENTS.clear();
            systemEnvLoaded = false;
        }
    }

    /**
     * Inject a value into the environment at runtime.
     * Useful for passing values between stacks.
     */
    public static void put(String key, String value) {
        synchronized (Environment.class) {
            RAW_ENV.put(key, value);
            CACHE.put(key, value); // Update cache directly
        }
    }

    // ---------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------

    private static void ensureSystemEnvLoaded() {
        if (!systemEnvLoaded) {
            synchronized (Environment.class) {
                if (!systemEnvLoaded) {
                    RAW_ENV.putAll(System.getenv());
                    systemEnvLoaded = true;
                }
            }
        }
    }

    private static void mergeNonSystemOverrides(Map<String, String> vars) {
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            if (!System.getenv().containsKey(entry.getKey())) {
                RAW_ENV.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Map<String, String> loadFromUrl(URL url, String sourceName) {
        Map<String, String> map = new HashMap<>();

        try (java.io.InputStream is = url.openStream();
                BufferedReader br = new BufferedReader(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isBlank() || line.startsWith("#"))
                    continue;

                int idx = line.indexOf('=');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    map.put(key, val);
                }
            }
        } catch (IOException ex) {
            throw new EnvironmentException("Failed to read env stream: " + sourceName, ex);
        }

        return map;
    }

    private static Map<String, String> loadFromFile(Path file) {
        Map<String, String> map = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isBlank() || line.startsWith("#"))
                    continue;

                int idx = line.indexOf('=');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    map.put(key, val);
                }
            }
        } catch (IOException ex) {
            throw new EnvironmentException("Failed to read env file: " + file, ex);
        }

        return map;
    }

    // ---------------------------------------------------------------------
    // Convenience static methods
    // ---------------------------------------------------------------------

    public static boolean getEnvBoolean(String key) {
        return Boolean.parseBoolean(Environment.getInstance().get(key));
    }

    public static int getEnvInt(String key) {
        return Integer.parseInt(Environment.getInstance().get(key));
    }
}
