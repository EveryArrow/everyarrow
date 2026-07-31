package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.CallableAnnotationMetadata;
import com.everyarrow.eventengine.spi.annotations.ArtemisQueueSubscription;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.lang.reflect.Method;

/**
 * Maps @ArtemisQueueSubscription (METHOD) into ServiceCallDefinition metadata.
 */
public class ArtemisQueueAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // Method-level only. Nothing to do here.
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {
        if (method == null || def == null) return;

        ArtemisQueueSubscription sub = method.getAnnotation(ArtemisQueueSubscription.class);
        if (sub == null) return;
        CallableAnnotationMetadata.apply(sub, def);

        ServiceCallDefinition.QueueSubscriptionMeta meta = new ServiceCallDefinition.QueueSubscriptionMeta();
        meta.destination = sub.destination();
        meta.maxRetries = sub.maxRetries();
        meta.selector = trimToNull(sub.selector());

        // helpful for debugging / future dispatch selection
        meta.methodName = method.getName();
        meta.declaringClass = method.getDeclaringClass().getName();

        def.addQueueSubscription(meta);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}



/*

package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.spi.annotations.ArtemisQueueSubscription;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;

import java.lang.reflect.Method;

/**
 * Maps @ArtemisQueueSubscription (method-level) into ServiceCallDefinition metadata.

public class ArtemisQueueAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // Method-level only: scan methods for @ArtemisQueueSubscription
        for (Method m : clazz.getDeclaredMethods()) {
            ArtemisQueueSubscription sub = m.getAnnotation(ArtemisQueueSubscription.class);
            if (sub == null) continue;

            ServiceCallDefinition.QueueSubscriptionMeta meta = new ServiceCallDefinition.QueueSubscriptionMeta();
            meta.destination = sub.destination();
            meta.maxRetries = sub.maxRetries();

            // Annotation currently has no contentType(). Use a safe default.
            // If you later add contentType() to the annotation, update this line to:
            // meta.contentType = sub.contentType();
            meta.contentType = "application/json";

            def.addQueueSubscription(meta);
        }
    }
}
*/
