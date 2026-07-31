/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manifest-declared plugin health requirements.
 */
public class PluginHealthMetadata {

    private List<String> requiredServices = new ArrayList<>();
    private List<String> requiredConfig = new ArrayList<>();

    public List<String> getRequiredServices() {
        if (requiredServices == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(requiredServices);
    }

    public void setRequiredServices(List<String> requiredServices) {
        this.requiredServices = requiredServices;
    }

    public List<String> getRequiredConfig() {
        if (requiredConfig == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(requiredConfig);
    }

    public void setRequiredConfig(List<String> requiredConfig) {
        this.requiredConfig = requiredConfig;
    }
}
