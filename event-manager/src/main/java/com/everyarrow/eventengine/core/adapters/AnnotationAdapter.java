package com.everyarrow.eventengine.core.adapters;

import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.lang.reflect.Method;

/**
 * Generic adapter interface that maps annotations on classes/methods
 * into a ServiceCallDefinition.
 */
public interface AnnotationAdapter {

    default void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // default no-op
    }

    default void applyToMethod(Method method, ServiceCallDefinition def) {
        // default no-op
    }
}
