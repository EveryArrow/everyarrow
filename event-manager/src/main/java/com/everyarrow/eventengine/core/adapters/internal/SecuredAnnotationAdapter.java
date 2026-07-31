package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.spi.annotations.Secured;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.SecuredMeta;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Adapter for @Secured annotations.
 */
public class SecuredAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // Secured is method-only; nothing to do at class level
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {
        Secured ann = method.getAnnotation(Secured.class);
        if (ann == null) {
            return;
        }

        def.setSecuredAnnotation(ann);

        SecuredMeta s = new SecuredMeta();
        s.roles = Arrays.asList(ann.roles());
        s.scopes = Arrays.asList(ann.scopes());
        s.isPublic = ann.isPublic();
        def.getAnnotations().secured = s;
    }
}
