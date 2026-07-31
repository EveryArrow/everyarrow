/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.CallableAnnotationMetadata;
import com.everyarrow.eventengine.spi.annotations.PostgresNotification;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.lang.reflect.Method;

public class PostgresNotificationAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {
        if (method == null || def == null) {
            return;
        }

        PostgresNotification annotation = method.getAnnotation(PostgresNotification.class);
        if (annotation == null) {
            return;
        }

        CallableAnnotationMetadata.apply(annotation, def);
        def.setPostgresNotificationAnnotation(annotation);

        ServiceCallDefinition.PostgresNotificationMeta meta =
                new ServiceCallDefinition.PostgresNotificationMeta();
        meta.database = annotation.database();
        meta.channel = annotation.channel();
        meta.declaringClass = method.getDeclaringClass().getName();
        meta.methodName = method.getName();
        def.addPostgresNotification(meta);
    }
}
