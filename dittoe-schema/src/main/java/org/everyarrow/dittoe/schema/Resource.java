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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource represents the source or target of the data.
 * Resource can represent any of these types: API, File, Stream, Database.
 */
public class Resource implements Serializable {

    /**
     * ID of the resource.
     */
    private int resourceID;

    /**
     * The name of the resource.
     */
    private String resourceName;

    /**
     * The server object where the resource resides
     */
    private Server server;
    //by definition a resource can only have a read or write strategy.  We may want different strategies for
    //a single server with different resources.
    private String strategy;

    // how would different server namespace and strategy would be set
    /**
     * Properties belonging to the resource. For example: It can include the type of the file (csv, json, etc.)
     * in case of File resource type.
     */
    private Map<String, String> resourceProperties = new HashMap<>();
    /**
     * List of fields of the resource.
     */
    private List<Field> fields = new ArrayList<>();

    /**
     * Default constructor.
     */
    public Resource() {
    }

    /**
     * Constructs a new Resource with the specified parameters.
     *
     * @param resourceID         the unique identifier for the resource
     * @param resourceName       the name of the resource
     * @param server             the server associated with the resource
     * @param resourceProperties a map of properties related to the resource
     * @param fields             a list of fields associated with the resource
     */
    public Resource(int resourceID, String resourceName, Server server, Map<String, String> resourceProperties,
                    List<Field> fields) {
        this.resourceName = resourceName;
        this.resourceID = resourceID;
        this.server = server;
        this.resourceProperties = resourceProperties;
        this.fields = fields;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        //We want to validate this with a valid strategy.  This will have to be delayed until server
        //instanciation
        this.strategy = strategy;
    }

    /**
     * Gets the fields of the resource.
     *
     * @return List of all the field.
     */
    public List<Field> getFields() {
        return fields;
    }

    /**
     * Sets the fields of the resource.
     *
     * @param fields List of fields.
     */
    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    /**
     * Gets the name of the resource.
     *
     * @return Name of the resource.
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * Sets the name of the resource.
     *
     * @param resourceName Name that is to be set.
     */
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    /**
     * Gets the ID of the resource.
     *
     * @return ID of the resource.
     */
    public int getResourceID() {
        return resourceID;
    }

    /**
     * Sets the ID of the resource.
     *
     * @param resourceID the ID that is to be set.
     */
    public void setResourceID(int resourceID) {
        this.resourceID = resourceID;
    }

    /**
     * Gets the properties of the resource.
     *
     * @return Properties of the resource.
     */
    public Map<String, String> getResourceProperties() {
        return resourceProperties;
    }

    /**
     * Sets the properties of the resource.
     *
     * @param resourceProperties Key of the property.
     */
    public void setResourceProperties(Map<String, String> resourceProperties) {
        this.resourceProperties = resourceProperties;
    }

    /**
     * Gets the server associated with this resource.
     *
     * @return the server associated with this resource
     */
    public Server getServer() {
        return server;
    }

    /**
     * Sets the server associated with this resource.
     *
     * @param server the server to be associated with this resource
     */
    public void setServer(Server server) {
        this.server = server;
    }
}
