/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.google.gson.Gson;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads plugin registry documents from plugin JAR resources.
 */
public class PluginRegistryDocumentLoader {

    public static final String DEFAULT_PLUGIN_DOCUMENT_PATH = "META-INF/everyarrow/plugin.json";
    public static final String ROOT_PLUGIN_DOCUMENT_PATH = "plugin.json";

    private final Gson gson = new Gson();

    public Optional<PluginRegistryDocument> load(File pluginDirectory, PluginClassLoader classLoader) {
        Optional<PluginRegistryDocument> fromDirectory = loadFromDirectory(pluginDirectory);
        if (fromDirectory.isPresent()) {
            return fromDirectory;
        }

        return load(classLoader);
    }

    public Optional<PluginRegistryDocument> load(PluginClassLoader classLoader) {
        try (InputStream in = classLoader.getResourceAsStream(DEFAULT_PLUGIN_DOCUMENT_PATH)) {
            if (in == null) {
                return Optional.empty();
            }

            return parseDocument(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load plugin registry document from "
                    + DEFAULT_PLUGIN_DOCUMENT_PATH, e);
        }
    }

    private Optional<PluginRegistryDocument> loadFromDirectory(File pluginDirectory) {
        if (pluginDirectory == null) {
            return Optional.empty();
        }

        Path rootDocumentPath = pluginDirectory.toPath().resolve(ROOT_PLUGIN_DOCUMENT_PATH);
        if (Files.isRegularFile(rootDocumentPath)) {
            try (InputStream in = Files.newInputStream(rootDocumentPath)) {
                return parseDocument(in);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load plugin registry document from "
                        + rootDocumentPath.toAbsolutePath(), e);
            }
        }

        Path documentPath = pluginDirectory.toPath().resolve("META-INF").resolve("everyarrow").resolve("plugin.json");
        if (!Files.isRegularFile(documentPath)) {
            return Optional.empty();
        }

        try (InputStream in = Files.newInputStream(documentPath)) {
            return parseDocument(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load plugin registry document from "
                    + documentPath.toAbsolutePath(), e);
        }
    }

    private Optional<PluginRegistryDocument> parseDocument(InputStream in) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            PluginRegistryDocument document = gson.fromJson(reader, PluginRegistryDocument.class);
            return Optional.ofNullable(document);
        }
    }
}
