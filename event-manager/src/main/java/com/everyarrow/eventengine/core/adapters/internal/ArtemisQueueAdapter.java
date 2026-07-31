package com.everyarrow.eventengine.core.adapters.internal;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.adapters.ProtocolAdapter;
import com.everyarrow.eventengine.core.adapters.internal.queue.QueueEnvelope;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.core.services.runtime.ServiceCallSource;
import com.everyarrow.eventengine.core.services.runtime.PerchRuntimeMetadata;
import com.everyarrow.eventengine.core.services.runtime.EventManagerServiceIdentity;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.utilities.environment.Environment;
import com.everyarrow.utilities.queues.DeadLetterDirective;
import com.everyarrow.utilities.queues.QueueAck;
import com.everyarrow.utilities.queues.QueueMessage;
import com.everyarrow.utilities.queues.RetryDirective;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueClient;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueConfig;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueConsumer;
import com.everyarrow.utilities.queues.artemis.ArtemisQueueProducer;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Artemis queue adapter.
 *
 * Uses Utilities queue primitives and dispatches messages by registry serviceType.
 */
public class ArtemisQueueAdapter implements ProtocolAdapter {

    private final Environment env = Environment.getInstance();

    private final List<AutoCloseable> handles = new ArrayList<>();
    private Engine engine;

    public ArtemisQueueAdapter() {
    }

    ArtemisQueueAdapter(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void start(Engine engine) {
        this.engine = engine;

        String enabled = env.get("EA_QUEUE_ENABLED", "true");
        if (!Boolean.parseBoolean(enabled)) {
            System.out.println("ArtemisQueueAdapter disabled by EA_QUEUE_ENABLED=false");
            return;
        }

        // Discover queue subscriptions from the registry.
        for (ServiceCallDefinition def : engine.getRegistry().listDefinitions()) {
            if (!def.hasQueueSubscriptions()) continue;

            for (ServiceCallDefinition.QueueSubscriptionMeta sub : def.getAnnotations().queueSubscriptions) {
                startConsumer(def.getServiceType(), sub.destination, sub.maxRetries, sub.selector);
            }
        }

        if (handles.isEmpty()) {
            System.out.println("ArtemisQueueAdapter started. No queue subscriptions registered.");
        } else {
            System.out.println("ArtemisQueueAdapter started. consumers=" + handles.size());
        }
    }

    private void startConsumer(String registeredServiceType, String destination, int maxRetries, String selector) {
        String brokerUrl = env.require("JMS_BROKER_URL");
        String user = trimToNull(env.get("JMS_USERNAME", ""));
        String pass = trimToNull(env.get("JMS_PASSWORD", ""));
        long receiveTimeoutMs = Long.parseLong(env.get("JMS_RECEIVE_TIMEOUT_MS", "250"));

        ArtemisQueueConfig cfg = new ArtemisQueueConfig(brokerUrl, user, pass, receiveTimeoutMs);

        ArtemisQueueClient client = new ArtemisQueueClient(cfg);
        ArtemisQueueProducer producer = new ArtemisQueueProducer(client);
        ArtemisQueueConsumer consumer = new ArtemisQueueConsumer(cfg, client, producer, destination, selector);

        consumer.start((QueueMessage msg, QueueAck ack) ->
                handleMessage(registeredServiceType, destination, maxRetries, msg, ack));

        handles.add(() -> {
            try { consumer.stop(); } catch (Exception ignored) {}
            try { consumer.close(); } catch (Exception ignored) {}
            try { producer.close(); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        });
    }

    private void handleMessage(String registeredServiceType, String destination, int maxRetries,
                               QueueMessage msg, QueueAck ack) {
        try {
            String raw = new String(msg.body(), StandardCharsets.UTF_8);
            JsonObject envJson = QueueEnvelope.parse(raw);

            String serviceType;
            JsonObject params;
            if (envJson.has("serviceType")) {
                params = QueueEnvelope.paramsOrEmpty(envJson);
                serviceType = resolveServiceTypeForMessage(registeredServiceType, destination, envJson, params);
            } else {
                serviceType = registeredServiceType;
                params = envJson;  // whole message body is the params
            }

            ServiceCall call = new ServiceCall();
            call.setType(serviceType);
            call.setParams(params);
            call.setSource(ServiceCallSource.QUEUE);
            call.getMetadata().put("queue.destination", destination);
            new EventManagerServiceIdentity().applyCredential(call);
            PerchRuntimeMetadata.normalizeFromQueueAttributes(call, msg.attributes());

            engine.execute(call);

            ack.ack();

        } catch (Exception ex) {
            System.err.println("[ArtemisQueueAdapter] Handler error on queue=" + destination + ": " + ex);
            ex.printStackTrace(System.err);
            int attempt = readAttempt(msg.attributes());
            long delayMs = Long.parseLong(env.get("EA_QUEUE_RETRY_DELAY_MS", "1000"));

            if (attempt < Math.max(0, maxRetries)) {
                ack.retry(new RetryDirective(attempt + 1, Duration.ofMillis(delayMs), safe(ex.getMessage())));
            } else {
                ack.deadLetter(new DeadLetterDirective("max retries exceeded", ex.getClass().getName(), safe(ex.getMessage())));
            }
        }
    }

    private static int readAttempt(Map<String, String> attrs) {
        if (attrs == null) return 0;
        String v = attrs.get("ea_retry_attempt");
        if (v == null || v.isBlank()) return 0;
        try { return Integer.parseInt(v.trim()); } catch (Exception ignored) { return 0; }
    }

    private void ensureServiceTypeAllowed(String registeredServiceType, String requestedServiceType) {
        if (registeredServiceType == null || registeredServiceType.isBlank()) {
            throw new IllegalArgumentException("Queue subscription is missing registered serviceType");
        }
        if (requestedServiceType == null || requestedServiceType.isBlank()) {
            throw new IllegalArgumentException("Queue message serviceType is blank");
        }
        if (registeredServiceType.equals(requestedServiceType)) {
            return;
        }

        String configured = env.get("EA_QUEUE_SERVICE_TYPE_OVERRIDE_ALLOWLIST", "");
        boolean allowlisted = configured != null
                && java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .anyMatch(requestedServiceType::equals);
        if (!allowlisted) {
            throw new SecurityException("Queue message serviceType override is not allowed");
        }
    }

    String resolveServiceTypeForMessage(String registeredServiceType, JsonObject envelope) {
        return resolveServiceTypeForMessage(registeredServiceType, null, envelope, new JsonObject());
    }

    String resolveServiceTypeForMessage(String registeredServiceType,
                                        String destination,
                                        JsonObject envelope,
                                        JsonObject params) {
        String requestedServiceType = QueueEnvelope.requireServiceType(envelope);
        if (shouldPreserveRegisteredDispatch(registeredServiceType, destination, requestedServiceType)) {
            params.addProperty("_queueEnvelopeServiceType", requestedServiceType);
            return registeredServiceType;
        }
        if (!hasDefinition(requestedServiceType)) {
            return registeredServiceType;
        }
        ensureServiceTypeAllowed(registeredServiceType, requestedServiceType);
        return requestedServiceType;
    }

    private boolean shouldPreserveRegisteredDispatch(String registeredServiceType,
                                                     String destination,
                                                     String requestedServiceType) {
        String swarmControllerQueue = env.get("SWARM_CONTROLLER_QUEUE", "swarm.controller.commands");
        return "swarm.command.dispatch".equals(registeredServiceType)
                && swarmControllerQueue.equals(destination)
                && ("pipeline.heartbeat".equals(requestedServiceType)
                || "pipeline.status".equals(requestedServiceType)
                || "pipeline.log".equals(requestedServiceType)
                || "pipeline.error_count".equals(requestedServiceType)
                || "instance.status".equals(requestedServiceType));
    }

    private boolean hasDefinition(String serviceType) {
        if (engine == null) {
            throw new IllegalStateException("Queue adapter requires an Engine before resolving queue service types");
        }
        return engine.getRegistry().getDefinition(serviceType) != null
                || engine.getPluginManager().getDefinition(serviceType).isPresent();
    }

    private static String safe(String msg) {
        if (msg == null) return "error";
        String t = msg.trim();
        return t.isEmpty() ? "error" : t;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public void stop() {
        for (AutoCloseable h : handles) {
            try { h.close(); } catch (Exception ignored) {}
        }
        handles.clear();
    }
}
