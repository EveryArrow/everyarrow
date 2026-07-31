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


import java.util.Map;

/**
 * A staged behavior represents a task that is done in a specified stage.
 * If two or more staged behaviors can be executed in the same stage, then,
 * they will be executed based on their ascending order.
 */
public abstract class StagedBehavior extends Behavior {

    /**
     * Represents an external plugin.
     */
    private String pluginReference;

    /**
     * Order in which staged behaviors of same stage will be called.
     */
    private int order;

    /**
     * Stage type.
     */
    private StageType stageType;

    /**
     * This constructor can be used to supply the reference to an external plugin.
     *
     * @param pluginReference Plugin reference.
     */
    public StagedBehavior(String pluginReference) {
        this.pluginReference = pluginReference;
    }

    /**
     * Parameterized constructor for the staged behavior.
     *
     * @param name         Name of the behavior.
     * @param behaviorType Type of the behavior.
     * @param order        Order of the behavior.
     * @param stageType    Stage type of the behavior.
     * @param properties   Properties of the behavior.
     * @param description  Description of the behavior.
     */
    public StagedBehavior(String name, BehaviorType behaviorType, Map<String, String> properties,
                          String description, int order, StageType stageType) {
        super(name, behaviorType, properties, description);
        this.order = order;
        this.stageType = stageType;
    }

    /**
     * Gets the plugin reference of the staged behavior.
     *
     * @return Plugin reference of the staged behavior.
     */
    public String getPluginReference() {
        return this.pluginReference;
    }

    /**
     * Sets the plugin reference.
     *
     * @param pluginReference Plugin reference that is to be set.
     */
    public void setPluginReference(String pluginReference) {
        this.pluginReference = pluginReference;
    }

    /**
     * Gets the order of the staged behavior.
     *
     * @return Order of the staged behavior.
     */
    public int getOrder() {
        return this.order;
    }

    /**
     * Sets the order of the staged behavior.
     *
     * @param order Order that is to be set.
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Gets the stage type of the staged behavior.
     *
     * @return Stage type of the staged behavior.
     */
    public StageType getStageType() {
        return this.stageType;
    }

    /**
     * Sets the stage type of the staged behavior.
     *
     * @param stageType Stage type that is to be set.
     */
    public void setStageType(StageType stageType) {
        this.stageType = stageType;
    }
}
