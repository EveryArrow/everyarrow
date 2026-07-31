/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.core.services.runtime.GenericProcessHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Immutable provenance and identity view of one registered function candidate.
 */
public record FunctionCandidate(
        String serviceType,
        String namespace,
        String logicalName,
        Kind kind,
        FunctionRegistrationOrigin origin,
        String implementation,
        String source,
        String pluginId,
        String pluginVersion,
        FunctionContractIdentity contract,
        FunctionOverrideDeclaration overrideDeclaration,
        String handlerClass,
        String targetMethod,
        String database,
        String schema,
        String function,
        String jsonType,
        String signature,
        String route) {

    public enum Kind {
        CORE,
        JAVA_HANDLER,
        DB_FUNCTION,
        METADATA_ONLY
    }

    private static final Gson GSON = new Gson();

    static FunctionCandidate from(ServiceCallDefinition definition,
                                  FunctionRegistrationOrigin origin,
                                  String ownerPluginId) {
        String serviceType = requiredServiceType(definition);
        String handlerClass = firstNonBlank(
                definition.getHandlerClass(),
                definition.getClassName());
        String source = trimToNull(definition.getSource());
        Kind kind = kind(definition, origin, handlerClass);
        String implementation = implementation(kind, definition, handlerClass);
        FunctionContractIdentity contract =
                FunctionContractIdentity.from(definition);
        FunctionOverrideDeclaration overrideDeclaration =
                FunctionOverrideDeclaration.from(definition);
        String contractPluginId = contract == null ? null : contract.pluginId();

        int separator = serviceType.lastIndexOf('.');
        String namespace = separator < 0 ? null : serviceType.substring(0, separator);
        String logicalName = separator < 0 ? serviceType : serviceType.substring(separator + 1);

        return new FunctionCandidate(
                serviceType,
                namespace,
                logicalName,
                kind,
                origin,
                implementation,
                source,
                firstNonBlank(
                        trimToNull(ownerPluginId),
                        firstNonBlank(contractPluginId, pluginId(source))),
                contract == null ? null : contract.pluginVersion(),
                contract,
                overrideDeclaration,
                trimToNull(handlerClass),
                trimToNull(definition.getTargetMethodName()),
                trimToNull(definition.getDbDatabase()),
                trimToNull(definition.getDbSchema()),
                trimToNull(definition.getDbFunction()),
                trimToNull(definition.getJsonType()),
                signature(definition),
                route(definition));
    }

    String declarationKey() {
        return String.join("\u001F",
                origin.name(),
                implementation,
                blankToEmpty(source),
                blankToEmpty(pluginId),
                blankToEmpty(pluginVersion),
                contract == null ? "" : contract.declarationKey(),
                overrideDeclaration == null ? "" : overrideDeclaration.declarationKey(),
                blankToEmpty(jsonType),
                signature,
                blankToEmpty(route));
    }

    String sortKey() {
        return String.join("\u001F",
                serviceType.toLowerCase(Locale.ROOT),
                String.format("%04d", 999 - FunctionResolutionPolicy.priority(this)),
                origin.name(),
                kind.name(),
                implementation,
                blankToEmpty(source),
                blankToEmpty(pluginId),
                blankToEmpty(pluginVersion),
                contract == null ? "" : contract.declarationKey(),
                overrideDeclaration == null ? "" : overrideDeclaration.declarationKey(),
                signature,
                blankToEmpty(route));
    }

    boolean equivalentImplementation(FunctionCandidate other) {
        return other != null
                && implementation.equals(other.implementation)
                && equalsNullable(source, other.source)
                && equalsNullable(pluginId, other.pluginId)
                && equalsNullable(pluginVersion, other.pluginVersion)
                && java.util.Objects.equals(contract, other.contract)
                && java.util.Objects.equals(
                        overrideDeclaration,
                        other.overrideDeclaration)
                && equalsNullable(jsonType, other.jsonType)
                && signature.equals(other.signature)
                && equalsNullable(route, other.route);
    }

    public JsonObject toJson(boolean selected) {
        JsonObject out = new JsonObject();
        out.addProperty("selected", selected);
        out.addProperty("serviceType", serviceType);
        add(out, "namespace", namespace);
        out.addProperty("logicalName", logicalName);
        out.addProperty("kind", kind.name().toLowerCase(Locale.ROOT));
        out.addProperty("origin", origin.name().toLowerCase(Locale.ROOT));
        out.addProperty("implementation", implementation);
        add(out, "source", source);
        add(out, "pluginId", pluginId);
        add(out, "pluginVersion", pluginVersion);
        if (contract != null) {
            out.add("contract", contract.toJson());
        }
        if (overrideDeclaration != null) {
            out.add("override", overrideDeclaration.toJson());
        }
        add(out, "handlerClass", handlerClass);
        add(out, "targetMethod", targetMethod);
        add(out, "database", database);
        add(out, "schema", schema);
        add(out, "function", function);
        add(out, "jsonType", jsonType);
        out.addProperty("signature", signature);
        add(out, "route", route);
        return out;
    }

    static boolean isDbBacked(ServiceCallDefinition definition) {
        if (definition == null) {
            return false;
        }
        String handlerClass = firstNonBlank(
                definition.getHandlerClass(),
                definition.getClassName());
        return isGenericProcessHandler(handlerClass)
                || startsWith(definition.getSource(), "db:")
                || !isBlank(definition.getDbDatabase())
                || !isBlank(definition.getDbSchema())
                || !isBlank(definition.getDbFunction());
    }

    private static Kind kind(ServiceCallDefinition definition,
                             FunctionRegistrationOrigin origin,
                             String handlerClass) {
        if (origin == FunctionRegistrationOrigin.CORE) {
            return Kind.CORE;
        }
        if (isDbBacked(definition)) {
            return Kind.DB_FUNCTION;
        }
        if (!isBlank(handlerClass)) {
            return Kind.JAVA_HANDLER;
        }
        return Kind.METADATA_ONLY;
    }

    private static String implementation(Kind kind,
                                         ServiceCallDefinition definition,
                                         String handlerClass) {
        return switch (kind) {
            case CORE, JAVA_HANDLER -> "java:"
                    + firstNonBlank(handlerClass, "<unknown>")
                    + "#" + firstNonBlank(definition.getTargetMethodName(), "<default>");
            case DB_FUNCTION -> "db:"
                    + firstNonBlank(definition.getDbDatabase(), "<unknown>")
                    + "." + firstNonBlank(definition.getDbSchema(), "<unknown>")
                    + "." + firstNonBlank(definition.getDbFunction(), "<unknown>");
            case METADATA_ONLY -> "metadata:" + definition.getServiceType();
        };
    }

    private static String route(ServiceCallDefinition definition) {
        if (definition.hasRestRoute()) {
            String method = firstNonBlank(definition.getRestRoute().method, "GET")
                    .toUpperCase(Locale.ROOT);
            return method + " " + definition.getRestRoute().path;
        }
        if (definition.hasWebhook()) {
            return "WEBHOOK " + definition.getWebhook().path;
        }
        if (definition.hasWebSocketRoute()) {
            return "WEBSOCKET " + definition.getWebSocketRoute().path;
        }
        return null;
    }

    private static String signature(ServiceCallDefinition definition) {
        String material = firstNonBlank(definition.getJsonType(), "unknown")
                + "\u001F" + GSON.toJson(definition.getAnnotations());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:"
                    + HexFormat.of().formatHex(
                    digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String pluginId(String source) {
        if (source == null || !source.startsWith("plugin:")) {
            return null;
        }
        String remainder = source.substring("plugin:".length());
        int separator = remainder.indexOf(':');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    private static String requiredServiceType(ServiceCallDefinition definition) {
        if (definition == null
                || definition.getServiceType() == null
                || definition.getServiceType().isBlank()) {
            throw new IllegalArgumentException("definition.serviceType is required");
        }
        return definition.getServiceType().trim();
    }

    private static boolean isGenericProcessHandler(String handlerClass) {
        return GenericProcessHandler.class.getSimpleName().equals(handlerClass)
                || GenericProcessHandler.class.getName().equals(handlerClass);
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static String firstNonBlank(String primary, String secondary) {
        return isBlank(primary) ? secondary : primary;
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void add(JsonObject object, String property, String value) {
        if (value != null) {
            object.addProperty(property, value);
        }
    }
}
