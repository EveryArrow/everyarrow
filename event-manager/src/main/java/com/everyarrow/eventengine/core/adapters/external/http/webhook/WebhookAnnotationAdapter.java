package com.everyarrow.eventengine.core.adapters.external.http.webhook;

import com.everyarrow.eventengine.core.adapters.AnnotationAdapter;
import com.everyarrow.eventengine.core.adapters.CallableAnnotationMetadata;
import com.everyarrow.eventengine.spi.annotations.Webhook;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.WebhookMeta;

import java.lang.reflect.Method;

/**
 * Adapter for @Webhook annotations.
 */
public class WebhookAnnotationAdapter implements AnnotationAdapter {

    @Override
    public void applyToClass(Class<?> clazz, ServiceCallDefinition def) {
        // Webhook is method-only; nothing to do at class level
    }

    @Override
    public void applyToMethod(Method method, ServiceCallDefinition def) {
        Webhook ann = method.getAnnotation(Webhook.class);
        if (ann == null) {
            return;
        }

        def.setWebhookAnnotation(ann);
        CallableAnnotationMetadata.apply(ann, def);

        WebhookMeta w = new WebhookMeta();
        w.path = ann.path();
        def.getAnnotations().webhook = w;
    }
}
