/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.plugin;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans classes inside a plugin JAR and returns those matching a predicate.
 * Plugin JARs are trusted code and should come only from admin-controlled
 * directories. Load failures are intentionally visible so operators do not run
 * with a partially loaded plugin set.
 */
public class PluginScanner {

    public static List<Class<?>> scan(PluginClassLoader loader, Predicate<Class<?>> filter) throws IOException {
        List<Class<?>> out = new ArrayList<>();

        URL jarURL = new URL("jar:" + loader.getDescriptor().getJarPath().toUri().toURL() + "!/");
        JarURLConnection conn = (JarURLConnection) jarURL.openConnection();

        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (!e.getName().endsWith(".class")) continue;

                String className = e.getName().replace("/", ".").replace(".class", "");

                try {
                    Class<?> cls = loader.loadClass(className);
                    if (filter.test(cls)) {
                        out.add(cls);
                    }
                } catch (ClassNotFoundException | LinkageError | RuntimeException ex) {
                    throw new IOException("Failed to load plugin class " + className
                            + " from " + loader.getDescriptor().getJarPath(), ex);
                }
            }
        }

        return out;
    }
}
