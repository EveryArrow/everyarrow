/*
 * DITTOE SCHEMA:
 *
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */

package org.everyarrow.dittoe.schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ResourceDependencyMapping represents the mapping between a resource and the resources it depends on.
 * This class is used to track dependencies in data pipelines and ensure proper execution order.
 */
public class ResourceDependencyMapping implements Serializable {

    /**
     * The primary resource for which dependencies are being tracked.
     */
    private Resource resource;

    /**
     * List of resources that the primary resource depends on.
     * These dependencies must be processed before the primary resource.
     */
    private List<Resource> dependencies;

    /**
     * Default constructor.
     */
    public ResourceDependencyMapping() {
        this.dependencies = new ArrayList<>();
    }

    /**
     * Constructor with primary resource.
     *
     * @param resource The primary resource
     */
    public ResourceDependencyMapping(Resource resource) {
        this();
        this.resource = resource;
    }

    /**
     * Constructor with resource and initial dependencies.
     *
     * @param resource The primary resource
     * @param dependencies List of dependent resources
     */
    public ResourceDependencyMapping(Resource resource, List<Resource> dependencies) {
        this(resource);
        this.dependencies = dependencies;
    }

    /**
     * Gets the primary resource.
     *
     * @return The primary resource
     */
    public Resource getResource() {
        return resource;
    }


    /**
     * Gets the list of dependencies.
     *
     * @return List of dependent resources
     */
    public List<Resource> getDependencies() {
        return dependencies;
    }


    @Override
    public String toString() {
        return "ResourceDependencyMapping{" +
               "resource=" + (resource != null ? resource.getResourceName() : "null") +
               ", dependencies=" + dependencies.stream().map(Resource::getResourceName).toList() +
               '}';
    }
}
