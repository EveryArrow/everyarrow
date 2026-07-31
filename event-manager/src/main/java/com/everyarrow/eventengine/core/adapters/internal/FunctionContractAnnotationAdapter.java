/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.spi.annotations.FunctionContract;
import com.everyarrow.eventengine.spi.annotations.FunctionOverride;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps explicit function compatibility and override annotations into durable
 * service definition metadata.
 */
public final class FunctionContractAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition definition) {
        apply(clazz, definition);
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition definition) {
        apply(method, definition);
    }

    private void apply(AnnotatedElement element, ServiceCallDefinition definition) {
        FunctionContract contract = element.getAnnotation(FunctionContract.class);
        if (contract != null) {
            ServiceCallDefinition.FunctionContractMeta metadata =
                    new ServiceCallDefinition.FunctionContractMeta();
            metadata.namespace = contract.namespace();
            metadata.version = contract.version();
            metadata.signature = contract.signature();
            metadata.capabilities = new ArrayList<>(List.of(contract.capabilities()));
            definition.getAnnotations().functionContract = metadata;
        }

        FunctionOverride override = element.getAnnotation(FunctionOverride.class);
        if (override != null) {
            ServiceCallDefinition.FunctionOverrideMeta metadata =
                    new ServiceCallDefinition.FunctionOverrideMeta();
            metadata.targetServiceType = override.serviceType();
            metadata.targetPluginId = override.pluginId();
            metadata.versionRange = override.versionRange();
            definition.getAnnotations().functionOverride = metadata;
        }
    }
}
