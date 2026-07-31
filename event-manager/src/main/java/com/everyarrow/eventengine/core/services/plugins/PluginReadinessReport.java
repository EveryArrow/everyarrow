/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.plugin.PluginHealthResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Readiness and startup-health view over the plugin catalog.
 */
final class PluginReadinessReport {

    private final File pluginDirectory;
    private final PluginCatalog pluginCatalog;
    private final Set<String> requiredPluginIds;

    PluginReadinessReport(File pluginDirectory,
                          PluginCatalog pluginCatalog,
                          Set<String> requiredPluginIds) {
        this.pluginDirectory = pluginDirectory;
        this.pluginCatalog = pluginCatalog;
        this.requiredPluginIds = requiredPluginIds == null ? Set.of() : Set.copyOf(requiredPluginIds);
    }

    JsonObject readinessJson() {
        JsonObject json = new JsonObject();
        json.addProperty("pluginRoot", pluginDirectory == null ? null : pluginDirectory.getAbsolutePath());

        JsonArray active = new JsonArray();
        for (String pluginId : pluginCatalog.loadedPluginNames()) {
            active.add(pluginId);
        }
        json.add("activePlugins", active);

        JsonArray activePlatform = new JsonArray();
        for (LoadedPlugin plugin : pluginCatalog.loadedPlugins()) {
            if (plugin.isRequiredPlatformPlugin()) {
                activePlatform.add(plugin.getEffectivePluginId());
            }
        }
        json.add("activePlatformPlugins", activePlatform);

        JsonArray quarantined = new JsonArray();
        for (String pluginId : pluginCatalog.quarantinedPluginNames()) {
            quarantined.add(pluginId);
        }
        json.add("quarantinedPlugins", quarantined);

        JsonArray quarantinedPlatform = new JsonArray();
        for (LoadedPlugin plugin : pluginCatalog.quarantinedPlugins()) {
            if (plugin.isRequiredPlatformPlugin()) {
                quarantinedPlatform.add(plugin.getEffectivePluginId());
            }
        }
        json.add("quarantinedPlatformPlugins", quarantinedPlatform);

        JsonArray required = new JsonArray();
        JsonArray missingRequired = new JsonArray();
        for (String pluginId : requiredPluginIds.stream().sorted().toList()) {
            required.add(pluginId);
            if (pluginCatalog.getLoaded(pluginId) == null) {
                missingRequired.add(pluginId);
            }
        }
        json.add("requiredPlugins", required);
        json.add("missingRequiredPlugins", missingRequired);

        JsonArray health = new JsonArray();
        for (LoadedPlugin plugin : pluginCatalog.loadedPlugins()) {
            plugin.getHealthResult().ifPresent(result -> health.add(result.toJson()));
        }
        for (LoadedPlugin plugin : pluginCatalog.quarantinedPlugins()) {
            plugin.getHealthResult().ifPresent(result -> health.add(result.toJson()));
        }
        json.add("pluginHealth", health);
        return json;
    }

    void printStartupHealthSummary() {
        List<String> activePluginIds = new ArrayList<>(pluginCatalog.loadedPluginNames());
        Collections.sort(activePluginIds);
        List<String> quarantinedPluginIds = new ArrayList<>(pluginCatalog.quarantinedPluginNames());
        Collections.sort(quarantinedPluginIds);

        System.out.println("[PluginManager] Plugin health summary");
        if (pluginDirectory != null) {
            System.out.println("  root: " + pluginDirectory.getAbsolutePath());
        }

        if (activePluginIds.isEmpty() && quarantinedPluginIds.isEmpty()) {
            System.out.println("  " + statusBox(PluginHealthResult.Status.WARN) + " no staged plugins discovered");
            return;
        }

        for (String pluginId : activePluginIds) {
            LoadedPlugin plugin = pluginCatalog.getLoaded(pluginId);
            PluginHealthResult result = plugin == null ? null : plugin.getHealthResult().orElse(null);
            PluginHealthResult.Status status = result == null ? PluginHealthResult.Status.UP : result.getStatus();
            System.out.printf("  %s %s%n", statusBox(status), pluginId);
        }

        if (quarantinedPluginIds.isEmpty()) {
            return;
        }

        System.err.println("============================================================");
        System.err.printf("[PluginManager] ALERT: %d QUARANTINED PLUGIN(S) DETECTED%n",
                quarantinedPluginIds.size());
        System.err.println("[PluginManager] Quarantined plugins are inactive. See secured GET /core/ready for full details.");
        for (String pluginId : quarantinedPluginIds) {
            LoadedPlugin plugin = pluginCatalog.getQuarantined(pluginId);
            PluginHealthResult result = plugin == null ? null : plugin.getHealthResult().orElse(null);
            PluginHealthResult.Status status = result == null
                    ? PluginHealthResult.Status.DOWN
                    : result.getStatus();
            System.err.printf("  %s %s%n", statusBox(status), pluginId);
            if (result == null) {
                System.err.println("      No health details were reported.");
                continue;
            }

            boolean printedProblemCheck = false;
            for (PluginHealthResult.Check check : result.getChecks()) {
                if (check.getStatus() == PluginHealthResult.Status.UP) {
                    continue;
                }
                printedProblemCheck = true;
                String message = check.getMessage() == null || check.getMessage().isBlank()
                        ? ""
                        : " - " + check.getMessage();
                System.err.printf("      %s %s%s%n", statusBox(check.getStatus()), check.getName(), message);
            }
            if (!printedProblemCheck) {
                System.err.println("      No failing check details were reported.");
            }
        }
        System.err.println("============================================================");
    }

    private String statusBox(PluginHealthResult.Status status) {
        if (status == PluginHealthResult.Status.UP) {
            return color("[OK]", "\u001B[32m");
        }
        if (status == PluginHealthResult.Status.WARN) {
            return color("[WARN]", "\u001B[33m");
        }
        return color("[DOWN]", "\u001B[31m");
    }

    private String color(String text, String color) {
        return color + text + "\u001B[0m";
    }
}
