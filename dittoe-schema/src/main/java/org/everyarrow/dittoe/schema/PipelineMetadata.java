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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The PipelineMetaData class is the main structure and represents a DITTOE Schema.
 * The purpose of this schema is to allow a single structure to be allowed to define
 * any data integration routine whether that routine is within an external tool such as NiFi, SSIS, Airflow. etc.
 * or a custom software implementation. Within custom software implementation, it can be used to define
 * distinct sets of behaviors that are specific to a data pipeline within an organization. The PMD can be exported
 * to a JSONSchema as well as YAML document structure. It is envisioned that this object will have a serialized
 * representation stored in an external data source. In an internally derived custom system, the PMD would be
 * retrieved and deserialized upon receiving a signal from a FileWatcher, Scheduler, Webhook or Queue. In an external
 * system this document can be injected into the data pipeline and be used to set external parameters in the external
 * system.
 */
public class PipelineMetadata extends PipelineMetadataBase {
    /**
     * This represents the source of the data for pipeline
     * A target can be generally any datasource that can be broken down into
     * Files, APIS, Databases, and Streams. In DITTOE we call these FADS
     * They are not fads of course, because these data source types
     * are the only ways to store information in the modern computing age.
     */
    private Resource sourceResourceToProcess;
    /**
     * This represents the target of the data for pipeline
     * A target can be generally any datasource that can be broken down into
     * Files, APIS, Databases, and Streams. In DITTOE we call these FADS
     * They are not fads of course, because these data source types
     * are the only ways to store information in the modern computing age.
     */
    private Resource targetResourceToProcess;
    /**
     * The templateType is an enum whose values represent an abstract concept that relate source to target.
     * In a custom implementation of this framework the TemplateType could be something akin to
     * SystemAtoTypeB. For example SalesForceToFile, or FileToMyHRDatabase. To implement custom types
     * you would modify the TemplateType enum items. If you use DITTOE without modifications we provide
     * four 16 standard templates that represent all combinations.
     * Source To Target representations for FADS (Files, APIs, Databases, Streams).
     */
    private TemplateType templateType;
    /**
     * The SourceToTarget Map holds the intersectional mapping between source and target
     * on an individual field level. This can be a basic fieldA from source becomes fieldA in target.
     * We also could instead say fieldA which is type int becomes a String named FieldC in target
     * Finally, we can use a transformational expressions to transform SourceToTarget. The expressions can
     * be customized or used in construction with Data Expression Analyst Language (DEAL).
     */
    private List<SourceToTargetMapping> S2TMap = new ArrayList<>();

    /**
     * Default constructor.
     */
    public PipelineMetadata() {
    }

    /**
     * Gets the source resource of Metadata.
     *
     * @return Returns a resource object contained within a PMD related to the source system.
     */
    public Resource getSourceResourceToProcess() {
        return sourceResourceToProcess;
    }

    /**
     * Sets a source resource in a Process Metadata Document.
     *
     * @param sourceResourceToProcess The source resource to set.
     */
    public void setSourceResourceToProcess(Resource sourceResourceToProcess) {
        this.sourceResourceToProcess = sourceResourceToProcess;
        applyConnectionDefaults(sourceResourceToProcess);
    }

    /**
     * Gets the target resource of Metadata.
     *
     * @return Returns a resource object contained within a PMD related to the target system.
     */
    public Resource getTargetResourceToProcess() {
        return targetResourceToProcess;
    }

    /**
     * Sets the target resource in a Process Metadata Document.
     *
     * @param targetResourceToProcess The target resource to set.
     */
    public void setTargetResourceToProcess(Resource targetResourceToProcess) {
        this.targetResourceToProcess = targetResourceToProcess;
        applyConnectionDefaults(targetResourceToProcess);
    }

    @Override
    public void setGlobalProperties(Map<String, String> globalProperties) {
        super.setGlobalProperties(globalProperties);
        applyConnectionDefaults(sourceResourceToProcess);
        applyConnectionDefaults(targetResourceToProcess);
    }

    private void applyConnectionDefaults(Resource resource) {
        if (resource != null && resource.getServer() != null) {
            resource.getServer().setConnectionDefaults(getGlobalProperties());
        }
    }

    /**
     * Gets the template type of Metadata.
     *
     * @return Template type of the Metadata.
     */
    public TemplateType getTemplateType() {
        return templateType;
    }

    /**
     * Sets a template type in a Process Metadata Document.
     *
     * @param templateType The template type to set.
     */
    public void setTemplateType(TemplateType templateType) {
        this.templateType = templateType;
    }

    /**
     * Gets the source to target mapping of Metadata.
     *
     * @return Source to target mapping of the Metadata.
     */
    public List<SourceToTargetMapping> getS2TMap() {
        return this.S2TMap;
    }

    /**
     * Sets the source to target mapping in a Process Metadata Document.
     *
     * @param s2TMap The source to target mapping to set.
     */
    public void setS2TMap(List<SourceToTargetMapping> s2TMap) {
        this.S2TMap = s2TMap;
    }
}
