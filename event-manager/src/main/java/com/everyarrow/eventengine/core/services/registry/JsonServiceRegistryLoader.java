/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.DbDiscoveredService;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads DB-function route definitions from JSON files on the classpath.
 *
 * Registry JSON can be either:
 *   1) an array of objects matching the DbDiscoveredService record shape:
 *   database, schema, function, serviceType, httpMethod, routePath, description
 *   2) a schema document with:
 *   schema: { database, name }, services: [...]
 *
 * Entries are converted via ServiceCallDefinition.fromDb() so they are routed
 * through GenericProcessHandler just like DB-registry-discovered services.
 */
public final class JsonServiceRegistryLoader {

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<DbDiscoveredService>>() {}.getType();
    private static final String LENIENT_ROUTE_SECURITY_CONFIG = "EA_LOCAL_DEV_LENIENT_DB_ROUTE_SECURITY";

    private JsonServiceRegistryLoader() {}

    /**
     * Load service definitions from a classpath JSON resource.
     *
     * @param resourcePath classpath resource path, e.g. "service_registry_db.json"
     * @return list of ServiceCallDefinition instances; empty if the resource is missing
     */
    public static List<ServiceCallDefinition> loadFromClasspath(String resourcePath) {
        return load(resourcePath);
    }

    public static List<ServiceCallDefinition> load(String resourcePath) {
        InputStream in = JsonServiceRegistryLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        if (in == null) {
            return Collections.emptyList();
        }

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return readDefinitions(reader, resourcePath);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse JSON service registry '"
                    + resourcePath + "': " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Load service definitions from a resource inside a specific classloader.
     * Used by PluginManager to load per-plugin service_registry JSON from the plugin's own JAR.
     *
     * @param classLoader the plugin's classloader
     * @param resourcePath resource path inside the JAR, e.g. "META-INF/everyarrow/sample-plugin/service_registry.json"
     * @return list of ServiceCallDefinition instances; empty if the resource is missing
     */
    public static List<ServiceCallDefinition> loadFromClassLoader(ClassLoader classLoader, String resourcePath) {
        InputStream in = classLoader.getResourceAsStream(resourcePath);
        if (in == null) {
            return Collections.emptyList();
        }

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return readDefinitions(reader, resourcePath);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse plugin JSON service registry '"
                    + resourcePath + "': " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static List<ServiceCallDefinition> loadRequiredFromClassLoader(ClassLoader classLoader, String resourcePath) {
        InputStream in = classLoader.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Plugin JSON service registry resource not found: " + resourcePath);
        }

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return readDefinitions(reader, resourcePath);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse plugin JSON service registry '" + resourcePath + "'",
                    e);
        }
    }

    /**
     * Load service definitions from a file path on disk.
     *
     * @param filePath absolute or relative file path, e.g. "src/main/resources/service_registry_db.json"
     * @return list of ServiceCallDefinition instances; empty if the file is missing or unreadable
     */
    public static List<ServiceCallDefinition> loadFromFile(String filePath) {
        try (InputStream in = new FileInputStream(filePath);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return readDefinitions(reader, filePath);
        } catch (java.io.FileNotFoundException e) {
            return Collections.emptyList();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse JSON service registry file '" + filePath + "': " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static List<ServiceCallDefinition> loadRequiredFromFile(String filePath) {
        try (InputStream in = new FileInputStream(filePath);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return readDefinitions(reader, filePath);
        } catch (java.io.FileNotFoundException e) {
            throw new IllegalStateException("Plugin JSON service registry file not found: " + filePath, e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse plugin JSON service registry file '" + filePath + "'",
                    e);
        }
    }

    private static List<ServiceCallDefinition> readDefinitions(InputStreamReader reader, String sourceName) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root == null || root.isJsonNull()) {
            return Collections.emptyList();
        }

        SchemaScope schema = null;
        List<DbDiscoveredService> entries;

        if (root.isJsonArray()) {
            entries = GSON.fromJson(root, LIST_TYPE);
        } else if (root.isJsonObject()) {
            SchemaRegistryDocument document = GSON.fromJson(root, SchemaRegistryDocument.class);
            schema = document == null ? null : document.schema;
            entries = document == null ? null : document.services;
        } else {
            return Collections.emptyList();
        }

        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<ServiceCallDefinition> result = new ArrayList<>(entries.size());
        for (DbDiscoveredService entry : entries) {
            if (entry == null) {
                continue;
            }
            ServiceCallDefinition definition = ServiceCallDefinition.fromDb(normalize(entry, schema, sourceName));
            DbServiceRegistrationPolicy.validate(definition, sourceName);
            result.add(definition);
        }
        return result;
    }

    private static DbDiscoveredService normalize(DbDiscoveredService entry,
                                                 SchemaScope schema,
                                                 String sourceName) {
        String database = firstNonBlank(entry.database(), schemaDatabase(schema));
        String schemaName = firstNonBlank(entry.schema(), schemaName(schema));
        String function = firstNonBlank(entry.function(), functionFromServiceType(entry.serviceType()));
        String serviceType = firstNonBlank(entry.serviceType(), serviceType(database, schemaName, function));

        validateSchemaScope(schema, database, schemaName, serviceType, sourceName);

        return new DbDiscoveredService(
                database,
                schemaName,
                function,
                serviceType,
                entry.httpMethod(),
                entry.routePath(),
                entry.description(),
                null,
                entry.jsonType(),
                entry.annotations());
    }

    private static void validateSchemaScope(SchemaScope expected,
                                            String database,
                                            String schema,
                                            String serviceType,
                                            String sourceName) {
        if (expected == null || isBlank(expected.database) || isBlank(expected.name)) {
            return;
        }

        String expectedDatabase = expected.database.trim();
        String expectedSchema = expected.name.trim();
        if (expectedDatabase.equalsIgnoreCase(nullToEmpty(database))
                && expectedSchema.equalsIgnoreCase(nullToEmpty(schema))) {
            return;
        }

        if (allowLenientLocalRegistryValidation()) {
            System.out.println(
                    "Warning: Schema registry '" + sourceName + "' is scoped to "
                            + expectedDatabase + "." + expectedSchema
                            + " but contains serviceType '" + serviceType
                            + "' from registry " + nullToEmpty(database) + "." + nullToEmpty(schema)
                            + "; allowing in local dev");
            return;
        }

        throw new IllegalStateException(
                "Schema registry '" + sourceName + "' is scoped to "
                        + expectedDatabase + "." + expectedSchema
                        + " but contains serviceType '" + serviceType
                        + "' from registry " + nullToEmpty(database) + "." + nullToEmpty(schema));
    }

    private static String firstNonBlank(String primary, String secondary) {
        return isBlank(primary) ? secondary : primary;
    }

    private static String schemaDatabase(SchemaScope schema) {
        return schema == null ? null : schema.database;
    }

    private static String schemaName(SchemaScope schema) {
        return schema == null ? null : schema.name;
    }

    private static String functionFromServiceType(String serviceType) {
        if (isBlank(serviceType)) {
            return null;
        }
        String[] parts = serviceType.split("\\.");
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    private static String serviceType(String database, String schema, String function) {
        if (isBlank(database) || isBlank(schema) || isBlank(function)) {
            return null;
        }
        return database + "." + schema + "." + function;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean allowLenientLocalRegistryValidation() {
        return Boolean.parseBoolean(Environment.getInstance().get(LENIENT_ROUTE_SECURITY_CONFIG, "false"));
    }

    private static final class SchemaRegistryDocument {
        private SchemaScope schema;
        private List<DbDiscoveredService> services;
    }

    private static final class SchemaScope {
        private String database;

        @SerializedName(value = "name", alternate = {"schema", "schema_name"})
        private String name;
    }
}
