package com.everyarrow.eventengine.core.adapters.external.websocket;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.CallableAnnotationMetadata;
import com.everyarrow.eventengine.spi.annotations.WebSocketRoute;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.WebSocketRouteMeta;

import java.lang.reflect.Method;

/**
 * Adapter for @WebSocketRoute annotations.
 */
public class WebSocketRouteAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // WebSocketRoute is method-only; nothing to do at class level.
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {
        WebSocketRoute ann = method.getAnnotation(WebSocketRoute.class);
        if (ann == null) {
            return;
        }

        def.setWebSocketRouteAnnotation(ann);
        CallableAnnotationMetadata.apply(ann, def);

        WebSocketRouteMeta meta = new WebSocketRouteMeta();
        meta.path = ann.path();
        def.setWebSocketRoute(meta);
    }
}
