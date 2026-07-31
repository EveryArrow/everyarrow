package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.spi.annotations.Monitored;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.MonitoredMeta;

import java.lang.reflect.Method;

/**
 * Adapter for @Monitored annotations.
 */
public class MonitoredAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // Monitored is method-only; nothing to do at class level
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {
        Monitored ann = method.getAnnotation(Monitored.class);
        if (ann == null) {
            return;
        }

        def.setMonitoredAnnotation(ann);

        MonitoredMeta m = new MonitoredMeta();
        m.application = ann.application();
        m.system = ann.system();
        m.description = ann.description();
        def.getAnnotations().monitored = m;
    }
}
