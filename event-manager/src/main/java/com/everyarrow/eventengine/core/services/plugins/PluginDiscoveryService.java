/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginDiscoveryService {

    private static final String IGNORED_SOURCE_DIRECTORY = "Source";
    private static final String IGNORED_PREVIOUS_DIRECTORY = "previous";
    private static final String JAR_EXTENSION = ".jar";
    private static final String CLASS_EXTENSION = ".class";
    private static final String PLUGIN_DOCUMENT_FILE_NAME = "plugin.json";
    private static final String REGISTRY_FILE_SUFFIX = ".json";
    private static final String PLUGIN_SCAN_DEBUG_ENV = "EA_PLUGIN_SCAN_DEBUG";

    private final PluginRegistryDocumentLoader documentLoader = new PluginRegistryDocumentLoader();
    private final PluginRegistryValidator registryValidator = new PluginRegistryValidator();

    public List<LoadedPlugin> discoverPlugins(File pluginDirectory) {
        if (!isPluginRootDirectory(pluginDirectory)) {
            logMissingPluginDirectory(pluginDirectory);
            return List.of();
        }

        try (var paths = Files.walk(pluginDirectory.toPath(), 1)) {
            return paths
                    .filter(path -> isDiscoverablePluginDirectory(path, pluginDirectory.toPath()))
                    .sorted(Comparator.comparing(this::directoryName))
                    .map(Path::toFile)
                    .map(this::tryLoadPlugin)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            logPluginDirectoryScanFailure(pluginDirectory, e);
            return List.of();
        }
    }

    private boolean isPluginRootDirectory(File pluginDirectory) {
        return pluginDirectory != null && pluginDirectory.isDirectory();
    }

    private boolean isDiscoverablePluginDirectory(Path path, Path rootDirectory) {
        return isImmediateChildDirectory(path, rootDirectory)
                && !isIgnoredPluginDirectory(path);
    }

    private boolean isImmediateChildDirectory(Path path, Path rootDirectory) {
        return !path.equals(rootDirectory) && Files.isDirectory(path);
    }

    private boolean isIgnoredPluginDirectory(Path path) {
        String name = directoryName(path);
        return IGNORED_SOURCE_DIRECTORY.equalsIgnoreCase(name)
                || IGNORED_PREVIOUS_DIRECTORY.equalsIgnoreCase(name);
    }

    private String directoryName(Path path) {
        return path.getFileName().toString();
    }

    private Optional<LoadedPlugin> tryLoadPlugin(File pluginDir) {
        try {
            return loadPlugin(pluginDir);
        } catch (Exception e) {
            logPluginLoadFailure(pluginDir, e);
            return Optional.empty();
        }
    }

    private Optional<LoadedPlugin> loadPlugin(File pluginDir) throws IOException {
        String pluginDirectoryName = PluginPathSecurity.validatePluginId(pluginDir.getName());
        List<File> jarFiles = findJarFilesRecursively(pluginDir);
        PluginIntegrityVerifier.fromEnvironment(pluginDir.toPath().getParent()).verify(jarFiles);
        PluginClassLoader classLoader = createPluginClassLoader(jarFiles);
        LoadedPlugin loadedPlugin = createLoadedPlugin(pluginDir, pluginDirectoryName, classLoader);

        try {
            Optional<PluginRegistryDocument> documentOpt = documentLoader.load(pluginDir, classLoader);
            List<PluginServiceRegistrySource> serviceRegistrySources = discoverServiceRegistrySources(
                    pluginDir,
                    jarFiles,
                    classLoader,
                    documentOpt
            );

            for (PluginServiceRegistrySource source : serviceRegistrySources) {
                loadedPlugin.addServiceRegistrySource(source);
            }

            if (jarFiles.isEmpty() && documentOpt.isEmpty() && serviceRegistrySources.isEmpty()) {
                logNoPluginArtifactsFound(pluginDir);
                classLoader.close();
                return Optional.empty();
            }

            if (documentOpt.isPresent()) {
                loadPluginFromRegistryDocument(pluginDir, loadedPlugin, classLoader, documentOpt.get());
            }

            if (!jarFiles.isEmpty()) {
                loadPluginFromClassScan(pluginDir, loadedPlugin, jarFiles, classLoader);
            }

            return Optional.of(loadedPlugin);
        } catch (Exception e) {
            try {
                classLoader.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    private List<PluginServiceRegistrySource> discoverServiceRegistrySources(
            File pluginDir,
            List<File> jarFiles,
            PluginClassLoader classLoader,
            Optional<PluginRegistryDocument> documentOpt) throws IOException {

        LinkedHashMap<String, PluginServiceRegistrySource> sources = new LinkedHashMap<>();
        String pluginId = resolvePluginId(pluginDir, documentOpt);
        List<String> declaredRegistryResources = documentOpt
                .map(PluginRegistryDocument::getPlugin)
                .map(PluginMetadata::getRegistryResources)
                .orElse(List.of());

        if (declaredRegistryResources.isEmpty()) {
            for (PluginServiceRegistrySource source : discoverDirectoryRegistryFiles(pluginDir)) {
                sources.putIfAbsent(source.kind() + ":" + source.location(), source);
            }

            for (PluginServiceRegistrySource source : discoverLegacyDirectoryRegistryFiles(pluginDir, pluginId)) {
                sources.putIfAbsent(source.kind() + ":" + source.location(), source);
            }

            for (File jarFile : jarFiles) {
                for (PluginServiceRegistrySource source : discoverJarRegistryResources(jarFile, pluginId)) {
                    sources.putIfAbsent(source.kind() + ":" + source.location(), source);
                }
            }

            return new ArrayList<>(sources.values());
        }

        // A non-empty manifest registry list is the executable contract. Ignore
        // unrelated registry JSON that may be present in shaded dependencies.
        for (String resourcePath : declaredRegistryResources) {
            String normalized = PluginPathSecurity.normalizeRegistryResourcePath(resourcePath);
            Path stagedRegistryFile = pluginDir.toPath().resolve(normalized);
            if (Files.isRegularFile(stagedRegistryFile)) {
                PluginServiceRegistrySource source = PluginServiceRegistrySource.declaredFile(stagedRegistryFile);
                sources.putIfAbsent(source.kind() + ":" + source.location(), source);
                continue;
            }
            if (classLoader.getResource(normalized) == null) {
                throw new IllegalStateException("Declared plugin registry resource not found: " + normalized);
            }
            PluginServiceRegistrySource source = PluginServiceRegistrySource.declaredClasspathResource(normalized);
            sources.putIfAbsent(source.kind() + ":" + source.location(), source);
        }

        return new ArrayList<>(sources.values());
    }

    private String resolvePluginId(File pluginDir, Optional<PluginRegistryDocument> documentOpt) {
        if (documentOpt.isPresent()
                && documentOpt.get().getPlugin() != null
                && documentOpt.get().getPlugin().getId() != null
                && !documentOpt.get().getPlugin().getId().isBlank()) {
            return PluginPathSecurity.validatePluginId(documentOpt.get().getPlugin().getId());
        }
        return PluginPathSecurity.validatePluginId(pluginDir.getName());
    }

    private List<PluginServiceRegistrySource> discoverDirectoryRegistryFiles(File pluginDir) throws IOException {
        try (var paths = Files.list(pluginDir.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isServiceRegistryFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(PluginServiceRegistrySource::file)
                    .toList();
        }
    }

    private List<PluginServiceRegistrySource> discoverLegacyDirectoryRegistryFiles(File pluginDir, String pluginId) throws IOException {
        Path registryRoot = pluginDir.toPath()
                .resolve("META-INF")
                .resolve("everyarrow")
                .resolve(pluginId);

        if (!Files.isDirectory(registryRoot)) {
            return List.of();
        }

        try (var paths = Files.list(registryRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isServiceRegistryFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(PluginServiceRegistrySource::file)
                    .toList();
        }
    }

    private List<PluginServiceRegistrySource> discoverJarRegistryResources(File jarFile, String pluginId) throws IOException {
        String prefix = "META-INF/everyarrow/" + pluginId + "/";
        List<PluginServiceRegistrySource> sources = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }

                String fileName = Path.of(name).getFileName().toString();
                if (!isServiceRegistryFileName(fileName)) {
                    continue;
                }

                sources.add(PluginServiceRegistrySource.classpathResource(name));
            }
        }

        sources.sort(Comparator.comparing(PluginServiceRegistrySource::location));
        return sources;
    }

    private boolean isServiceRegistryFile(Path path) {
        return isServiceRegistryFileName(path.getFileName().toString());
    }

    private boolean isServiceRegistryFileName(String fileName) {
        // Collapsed schema plugins keep folder-named registry files such as sample-contact.json.
        return fileName.endsWith(REGISTRY_FILE_SUFFIX)
                && !PLUGIN_DOCUMENT_FILE_NAME.equalsIgnoreCase(fileName);
    }

    private PluginClassLoader createPluginClassLoader(List<File> jarFiles) throws IOException {
        URL[] jarUrls = jarFiles.stream()
                .map(this::toJarUrl)
                .toArray(URL[]::new);

        return new PluginClassLoader(jarUrls, getClass().getClassLoader());
    }

    private URL toJarUrl(File jarFile) {
        try {
            return jarFile.toURI().toURL();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create URL for JAR: " + jarFile.getAbsolutePath(), e);
        }
    }

    private LoadedPlugin createLoadedPlugin(File pluginDir, String pluginDirectoryName, PluginClassLoader classLoader) {
        PluginDescriptor descriptor = new PluginDescriptor(pluginDirectoryName, pluginDir);
        return new LoadedPlugin(descriptor, classLoader);
    }

    private void loadPluginFromRegistryDocument(
            File pluginDir,
            LoadedPlugin loadedPlugin,
            PluginClassLoader classLoader,
            PluginRegistryDocument document) {

        registryValidator.validate(document, classLoader);
        loadedPlugin.setRegistryDocument(document);
    }

    private void loadPluginFromClassScan(
            File pluginDir,
            LoadedPlugin loadedPlugin,
            List<File> jarFiles,
            PluginClassLoader classLoader) throws IOException {

        for (File jarFile : jarFiles) {
            scanJarFile(jarFile, classLoader, loadedPlugin);
        }
    }

    private void scanJarFile(File jarFile, PluginClassLoader classLoader, LoadedPlugin loadedPlugin) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!isClassEntry(entry)) {
                    continue;
                }

                String className = toClassName(entry);

                try {
                    Class<?> clazz = classLoader.loadClass(className);

                    if (isConcreteServiceCallHandler(clazz)) {
                        loadedPlugin.addDiscoveredHandlerClass(clazz);
                    }
                } catch (Throwable t) {
                    logSkippedClass(className, t);
                }
            }
        }
    }

    private boolean isClassEntry(JarEntry entry) {
        return !entry.isDirectory() && entry.getName().endsWith(CLASS_EXTENSION);
    }

    private String toClassName(JarEntry entry) {
        return entry.getName()
                .replace('/', '.')
                .substring(0, entry.getName().length() - CLASS_EXTENSION.length());
    }

    private boolean isConcreteServiceCallHandler(Class<?> clazz) {
        return ServiceCallHandler.class.isAssignableFrom(clazz)
                && !clazz.isInterface()
                && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())
                && !clazz.isEnum();
    }

    private List<File> findJarFilesRecursively(File pluginDir) throws IOException {
        try (var paths = Files.walk(pluginDir.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(this::isJarFile)
                    .sorted(Comparator.comparing(File::getAbsolutePath))
                    .toList();
        }
    }

    private boolean isJarFile(File file) {
        return file.getName().endsWith(JAR_EXTENSION);
    }

    private void logMissingPluginDirectory(File pluginDirectory) {
        System.out.println("Info: Plugin directory not found: " + pluginDirectory);
    }

    private void logPluginDirectoryScanFailure(File pluginDirectory, IOException e) {
        System.err.printf(
                "Error: Failed to scan plugin directory %s: %s%n",
                pluginDirectory.getAbsolutePath(),
                e.getMessage()
        );
        e.printStackTrace();
    }

    private void logPluginLoadFailure(File pluginDir, Exception e) {
        System.err.printf(
                "Error: Failed to load plugin %s: %s%n",
                pluginDir.getName(),
                e.getMessage()
        );
        e.printStackTrace();
    }

    private void logNoPluginArtifactsFound(File pluginDir) {
        System.out.println("Info: No plugin artifacts in plugin directory: " + pluginDir.getAbsolutePath());
    }

    private void logSkippedClass(String className, Throwable t) {
        if (!Boolean.parseBoolean(System.getenv(PLUGIN_SCAN_DEBUG_ENV))) {
            return;
        }
        System.err.printf("Warning: Skipping class %s (%s)%n", className, t.getMessage());
    }
}
