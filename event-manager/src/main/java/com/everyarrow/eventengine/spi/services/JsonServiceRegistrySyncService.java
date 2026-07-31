/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.spi.services;

import java.nio.file.Path;
import java.util.List;

/**
 * Runtime service for reloading JSON registry files into the live engine
 * registry.
 *
 * Database-schema discovery and code generation are deliberately outside this
 * runtime contract. This service only manages JSON files already supplied to the
 * running Event Manager.
 */
public interface JsonServiceRegistrySyncService {

    /**
     * Reload one managed JSON registry file into the live ServiceRegistry.
     */
    ReloadResult reloadFile(Path targetPath) throws Exception;

    /**
     * Reload all files previously loaded through this service instance.
     */
    List<ReloadResult> reloadManagedFiles() throws Exception;

    record ReloadResult(Path targetPath,
                        String registrySource,
                        int loadedCount) {
    }
}
