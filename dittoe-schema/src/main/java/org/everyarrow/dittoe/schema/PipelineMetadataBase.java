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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for PipeLineMetaData.
 */
public class PipelineMetadataBase implements Serializable {

    /**
     * The UUID associated with this Pipeline Metadata
     */
    private String id;

    /**
     * This is a collection of name value pairs that can be used as a grabbag to
     * store different properties that are not considered. In external systems
     * these would correspond to external properties that is injected at runtime
     * into external systems.
     */
    private Map<String, String> globalProperties = new HashMap<>();

    /**
     * Behaviors represent one of 18 known types of activities they may exist within a custom pipeline.
     * They are akin to droppable widgets in a Visual Programming System. There is an external widget behaviour
     * when using an external system. When used with an external etl or workflow solution such as Informatica,
     * NiFi, SSIS, AirFlow we can use behavior to define the properties of an external widget that is passed
     * at runtime into an external engine.
     */
    private List<Behavior> behaviors = new ArrayList<>();

    /**
     * A short name for the schema.
     */
    private String shortName;

    /**
     * Description about the schema.
     */
    private String description;

    /**
     * Represents the date it was created.
     */
    private LocalDate createDate;

    /**
     * Represents the actor who created it.
     */
    private String createdBy;

    /**
     * Represents the actor who last updated it.
     */
    private String lastUpdatedBy;

    /**
     * Represents the date it was last updated.
     */
    private LocalDate lastUpdatedDate;

    /**
     * Gets the global properties of the Process Metadata Document.
     *
     * @return Global properties of the Process Metadata Document.
     */
    public Map<String, String> getGlobalProperties() {
        return globalProperties;
    }

    /**
     * Sets the global properties of the Process Metadata Document.
     *
     * @param globalProperties The global properties that are to be set.
     */
    public void setGlobalProperties(Map<String, String> globalProperties) {
        this.globalProperties = globalProperties;
    }

    /**
     * Gets the behaviors of the Process Metadata Document.
     *
     * @return Behaviors of the Process Metadata Document.
     */
    public List<Behavior> getBehaviors() {
        return behaviors;
    }

    /**
     * Sets the behaviors of the Process Metadata Document.
     *
     * @param behaviors List of behaviors that are to be set.
     */
    public void setBehaviors(List<Behavior> behaviors) {
        this.behaviors = behaviors;
    }

    /**
     * Gets the short name of the Process Metadata Document.
     *
     * @return Short name of the Process Metadata Document.
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * Sets the short name of the Process Metadata Document.
     *
     * @param shortName Name that is to be set.
     */
    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    /**
     * Gets the description of the Process Metadata Document.
     *
     * @return The description of the Process Metadata Document.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the Process Metadata Document.
     *
     * @param description Description of the Process Metadata Document.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the created date of the Process Metadata Document.
     *
     * @return Created date of the Process Metadata Document.
     */
    public LocalDate getCreateDate() {
        return createDate;
    }

    /**
     * Sets the created date of the Process Metadata Document.
     *
     * @param createDate Created date that is to be set.
     */
    public void setCreateDate(LocalDate createDate) {
        this.createDate = createDate;
    }

    /**
     * Gets the actor who created the Process Metadata Document.
     *
     * @return The actor who created the Process Metadata Document.
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the actor who created the Process Metadata Document.
     *
     * @param createdBy The actor who created the Process Metadata Document.
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Gets the actor by whom the Process Metadata Document was last updated.
     *
     * @return The actor who updated the Process Metadata Document.
     */
    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    /**
     * Sets the actor by whom the Process Metadata Document was last updated.
     *
     * @param lastUpdatedBy The actor who updated the Process Metadata Document.
     */
    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    /**
     * Gets the last updated date of Process Metadata Document.
     *
     * @return The date Process Metadata Document was last updated.
     */
    public LocalDate getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    /**
     * Sets the last updated date of the Process Metadata Document.
     *
     * @param lastUpdatedDate The day Process Metadata Document was last updated.
     */
    public void setLastUpdatedDate(LocalDate lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
