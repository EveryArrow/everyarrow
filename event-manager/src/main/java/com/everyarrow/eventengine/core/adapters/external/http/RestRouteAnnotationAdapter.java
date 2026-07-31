package com.everyarrow.eventengine.core.adapters.external.http;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.CallableAnnotationMetadata;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.RestRouteMeta;

import java.lang.reflect.Method;

/**
 * Adapter for @RestRoute annotations.
 */
public class RestRouteAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // RestRoute is method-only; nothing to do at class level
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {
        RestRoute ann = method.getAnnotation(RestRoute.class);
        if (ann == null) {
            return;
        }

        def.setRestRouteAnnotation(ann);
        CallableAnnotationMetadata.apply(ann, def);

        RestRouteMeta r = new RestRouteMeta();
        r.path = ann.path();
        r.method = ann.method();
        def.getAnnotations().restRoute = r;
    }
}
