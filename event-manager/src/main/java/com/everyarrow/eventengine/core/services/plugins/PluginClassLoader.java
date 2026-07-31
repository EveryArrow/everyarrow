/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Class loader for a single external plugin artifact.
 *
 * Phase 1 behavior is intentionally simple:
 * - wraps URLClassLoader
 * - uses normal parent-first delegation
 * - loads from one plugin JAR URL
 *
 * Later this class can evolve to support:
 * - plugin metadata
 * - multiple URLs per plugin
 * - child-first rules for selected packages
 * - stricter lifecycle / unload behavior
 */
public class PluginClassLoader extends URLClassLoader {

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }
}