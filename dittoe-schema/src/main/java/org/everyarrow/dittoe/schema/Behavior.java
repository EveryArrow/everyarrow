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
import java.util.HashMap;
import java.util.Map;

/**
 * Behaviors represent one of 18 known types of activities they may exist within a custom pipeline.
 * They are akin to droppable widgets in a Visual Programming System. There is an external widget behaviour
 * when using an external system. When used with an external etl or workflow solution such as Informatica,
 * NiFi, SSIS, AirFlow we can use behavior to define the properties of an external widget that is passed
 * at runtime into an external engine.
 */
public class Behavior implements Serializable {

    /**
     * Name of the behavior.
     */
    private String name;

    /**
     * Type of the behavior.
     */
    private BehaviorType behaviorType;

    /**
     * Properties of the behavior.
     */
    private Map<String, String> properties = new HashMap<>();

    /**
     * Description of the behavior.
     */
    private String description;


    /**
     * Default constructor.
     */
    public Behavior() {
    }


    /**
     * Parameterized constructor.
     *
     * @param name         Name of the behavior.
     * @param behaviorType Type of the behavior.
     * @param properties   Properties of the behavior.
     * @param description  Description of the behavior.
     */
    public Behavior(String name, BehaviorType behaviorType, Map<String, String> properties, String description) {
        this.name = name;
        this.behaviorType = behaviorType;
        this.properties = properties;
        this.description = description;
    }

    /**
     * Gets the properties of the behavior.
     *
     * @return Properties of the behavior.
     */
    public Map<String, String> getProperties() {
        return properties;
    }


    /**
     * Sets the properties of the behavior.
     *
     * @param properties Properties that are to be set.
     */
    public void setProperties(Map<String, String> properties) {

        this.properties = properties;
    }

    /**
     * Gets the name of the behavior.
     *
     * @return name of the behavior.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the behavior.
     *
     * @param name Name that is to be set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets type of the behavior.
     *
     * @return type of the behavior.
     */
    public BehaviorType getBehaviorType() {
        return behaviorType;
    }

    /**
     * Sets type of the behavior.
     *
     * @param type Type of the behavior.
     */
    public void setBehaviorType(BehaviorType type) {
        behaviorType = type;
    }

   /**
     * Gets description of the behavior.
     *
     * @return Description of the behavior.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets description of the behavior.
     *
     * @param description Description to be set.
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
