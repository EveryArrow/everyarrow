/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.everyarrow.utilities.plugin;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Per-plugin class loader.
 */
public class PluginClassLoader extends URLClassLoader {

    private final PluginDescriptor descriptor;

    public PluginClassLoader(PluginDescriptor descriptor, ClassLoader parent) throws Exception {
        super(new URL[]{descriptor.getJarPath().toUri().toURL()}, parent);
        this.descriptor = descriptor;
    }

    public PluginDescriptor getDescriptor() {
        return descriptor;
    }
}
