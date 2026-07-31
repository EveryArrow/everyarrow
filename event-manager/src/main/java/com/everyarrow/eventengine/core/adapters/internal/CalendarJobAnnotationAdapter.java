package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.CallableAnnotationMetadata;
import com.everyarrow.eventengine.spi.annotations.CalendarJob;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.CalendarJobMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Annotation adapter for @CalendarJob.
 *
 * This adapter attaches method-level scheduler metadata to the unified
 * ServiceCallDefinition.Annotations block.
 */
public class CalendarJobAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // @CalendarJob is method-only, no class-level processing
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {

        CalendarJob ann = method.getAnnotation(CalendarJob.class);
        if (ann == null) return;
        CallableAnnotationMetadata.apply(ann, def);

        if (def.getAnnotations().calendarJobs == null) {
            def.getAnnotations().calendarJobs = new ArrayList<>();
        }

        ServiceCallDefinition.CalendarJobMeta meta = new ServiceCallDefinition.CalendarJobMeta();
        meta.methodName = method.getName();
        meta.cron = ann.cron();
        meta.timezone = ann.timezone();
        meta.startup = ann.startup();

        // REQUIRED: Fix test scheduling
        meta.declaringClass = method.getDeclaringClass().getName();

        meta.id = (ann.id() == null || ann.id().isBlank())
                ? def.getServiceType() + "#" + method.getName()
                : ann.id();

        def.getAnnotations().calendarJobs.add(meta);
    }
}
