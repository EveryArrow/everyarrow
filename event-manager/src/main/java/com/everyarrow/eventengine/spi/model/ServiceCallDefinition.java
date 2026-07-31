package com.everyarrow.eventengine.spi.model;

import com.everyarrow.eventengine.spi.annotations.Monitored;
import com.everyarrow.eventengine.spi.annotations.PostgresNotification;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.Secured;
import com.everyarrow.eventengine.spi.annotations.Webhook;
import com.everyarrow.eventengine.spi.annotations.WebSocketRoute;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Canonical metadata model describing one service call.
 *
 * This class is populated entirely by AnnotationAdapters at engine
 * initialization.
 * It stores:
 * - Raw annotation instances (for advanced logic or debugging)
 * - Processed metadata ready for routing/security/scheduling
 */
public class ServiceCallDefinition {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---------------------------------------------------------------------
    // Raw annotation instances (adapters expect these to exist)
    // ---------------------------------------------------------------------

    private transient RestRoute restRouteAnnotation;
    private transient Webhook webhookAnnotation;
    private transient WebSocketRoute webSocketRouteAnnotation;
    private transient PostgresNotification postgresNotificationAnnotation;
    private transient Monitored monitoredAnnotation;
    private transient Secured securedAnnotation;

    // ---------------------------------------------------------------------
    // Core identity
    // ---------------------------------------------------------------------

    private String serviceType;
    private String className; // handler class
    private String handlerClass; // actual implementation class
    private String category; // core, plugin, db
    private String source; // jar/module source
    private boolean active = true;
    private String jsonType; // json or jsonb
    private transient String dbDatabase;
    private transient String dbSchema;
    private transient String dbFunction;

    /**
     * When non-null, Engine.execute() will invoke this specific method on the handler
     * instance instead of the default execute() contract method.
     * Set by AnnotationDefinitionBuilder when @ServiceCallName is found on a method.
     */
    private String targetMethodName;

    // ---------------------------------------------------------------------
    // Unified processed metadata block
    // ---------------------------------------------------------------------

    public Annotations annotations = new Annotations();

    public static class Annotations {
        public CallMeta call;
        public PerchMeta perch;
        public DatabaseCorrelationMeta databaseCorrelation;
        public FunctionContractMeta functionContract;
        public FunctionOverrideMeta functionOverride;
        public SecuredMeta secured;
        public MonitoredMeta monitored;
        public RestRouteMeta restRoute;
        public WebhookMeta webhook;
        public WebSocketRouteMeta webSocketRoute;
        public List<CalendarJobMeta> calendarJobs = new ArrayList<>();

        // ADDED: first-order queue subscriptions
        public List<QueueSubscriptionMeta> queueSubscriptions = new ArrayList<>();
        public List<PostgresNotificationMeta> postgresNotifications = new ArrayList<>();
    }

    // ---------------------------------------------------------------------
    // Metadata Structures
    // ---------------------------------------------------------------------

    public static class CallMeta {
        public String applicationKey;
        public boolean open;
    }

    public static class PerchMeta {
        public String applicationName;
        public String systemName;
        public String description;
    }

    /**
     * Opt-in contract for server-derived correlation written to DB-function input.
     */
    public static class DatabaseCorrelationMeta {
        public String column;
        public String scope;
    }

    /**
     * Stable function-level compatibility metadata shared by generated
     * functions and explicit plugin implementations.
     */
    public static class FunctionContractMeta {
        public String namespace;
        public String version;
        public String signature;
        public List<String> capabilities = new ArrayList<>();
        public String pluginId;
        public String pluginVersion;
    }

    /**
     * Explicit relationship from a plugin implementation to its generated
     * function fallback.
     */
    public static class FunctionOverrideMeta {
        public String targetServiceType;
        public String targetPluginId;
        public String versionRange;
    }

    public static class SecuredMeta {
        public List<String> roles;
        public List<String> scopes;
        public boolean isPublic;
    }

    public static class MonitoredMeta {
        public String application;
        public String system;
        public String description;
    }

    public static class RestRouteMeta {
        public String path;
        public String method;
    }

    public static class WebhookMeta {
        public String path;
    }

    public static class WebSocketRouteMeta {
        public String path;
    }

    public static class CalendarJobMeta {
        public String id;
        public String methodName;
        public String cron;
        public String timezone;
        public boolean startup;

        // REQUIRED FOR TEST SCHEDULER
        public String declaringClass;
    }

    public static class QueueSubscriptionMeta {
        public String destination;
        public int maxRetries = 3;
        public String selector;

        // helps tests + debugging + future method-specific dispatch
        public String declaringClass;
        public String methodName;
    }

    public static class PostgresNotificationMeta {
        public String database;
        public String channel;
        public String declaringClass;
        public String methodName;
    }
    // ---------------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------------

    public ServiceCallDefinition() {
    }

    public ServiceCallDefinition(String serviceType, String className, String category) {
        this.serviceType = serviceType;
        this.className = className;
        this.category = category;
        this.active = true;
    }

    // ---------------------------------------------------------------------
    // Standard getters/setters
    // ---------------------------------------------------------------------

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getHandlerClass() {
        return handlerClass;
    }

    public void setHandlerClass(String handlerClass) {
        this.handlerClass = handlerClass;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDbDatabase() {
        return dbDatabase;
    }

    public void setDbDatabase(String dbDatabase) {
        this.dbDatabase = dbDatabase;
    }

    public String getDbSchema() {
        return dbSchema;
    }

    public void setDbSchema(String dbSchema) {
        this.dbSchema = dbSchema;
    }

    public String getDbFunction() {
        return dbFunction;
    }

    public void setDbFunction(String dbFunction) {
        this.dbFunction = dbFunction;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getJsonType() {
        return jsonType;
    }

    public void setJsonType(String jsonType) {
        this.jsonType = jsonType;
    }

    public String getTargetMethodName() {
        return targetMethodName;
    }

    public void setTargetMethodName(String targetMethodName) {
        this.targetMethodName = targetMethodName;
    }

    public boolean hasTargetMethod() {
        return targetMethodName != null && !targetMethodName.isBlank();
    }

    public Annotations getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Annotations annotations) {
        this.annotations = annotations;
    }

    // ---------------------------------------------------------------------
    // Raw annotation setters (adapters call these)
    // ---------------------------------------------------------------------

    public void setRestRouteAnnotation(RestRoute ann) {
        this.restRouteAnnotation = ann;
    }

    public void setWebhookAnnotation(Webhook ann) {
        this.webhookAnnotation = ann;
    }

    public void setWebSocketRouteAnnotation(WebSocketRoute ann) {
        this.webSocketRouteAnnotation = ann;
    }

    public void setPostgresNotificationAnnotation(PostgresNotification ann) {
        this.postgresNotificationAnnotation = ann;
    }

    public void setMonitoredAnnotation(Monitored ann) {
        this.monitoredAnnotation = ann;
    }

    public void setSecuredAnnotation(Secured ann) {
        this.securedAnnotation = ann;
    }

    // ---------------------------------------------------------------------
    // Processed metadata setters (adapters call these)
    // ---------------------------------------------------------------------

    public void setRestRoute(RestRouteMeta meta) {
        this.annotations.restRoute = meta;
    }

    public void setWebhook(WebhookMeta meta) {
        this.annotations.webhook = meta;
    }

    public void setWebSocketRoute(WebSocketRouteMeta meta) {
        this.annotations.webSocketRoute = meta;
    }

    public void setMonitored(MonitoredMeta meta) {
        this.annotations.monitored = meta;
    }

    public void setSecured(SecuredMeta meta) {
        this.annotations.secured = meta;
    }

    public void addCalendarJob(CalendarJobMeta job) {
        if (this.annotations.calendarJobs == null) {
            this.annotations.calendarJobs = new ArrayList<>();
        }
        this.annotations.calendarJobs.add(job);
    }

    // ADDED: required by ArtemisQueueAnnotationAdapter / ArtemisQueueAdapter
    public void addQueueSubscription(QueueSubscriptionMeta meta) {
        if (this.annotations.queueSubscriptions == null) {
            this.annotations.queueSubscriptions = new ArrayList<>();
        }
        this.annotations.queueSubscriptions.add(meta);
    }

    public void addPostgresNotification(PostgresNotificationMeta meta) {
        if (this.annotations.postgresNotifications == null) {
            this.annotations.postgresNotifications = new ArrayList<>();
        }
        this.annotations.postgresNotifications.add(meta);
    }

    // ---------------------------------------------------------------------
    // Convenience accessors
    // ---------------------------------------------------------------------

    public RestRouteMeta getRestRoute() {
        return annotations.restRoute;
    }

    public WebhookMeta getWebhook() {
        return annotations.webhook;
    }

    public WebSocketRouteMeta getWebSocketRoute() {
        return annotations.webSocketRoute;
    }

    public List<CalendarJobMeta> getCalendarJobs() {
        if (annotations.calendarJobs == null)
            return Collections.emptyList();
        return Collections.unmodifiableList(annotations.calendarJobs);
    }

    // ADDED
    public List<QueueSubscriptionMeta> getQueueSubscriptions() {
        if (annotations.queueSubscriptions == null)
            return Collections.emptyList();
        return Collections.unmodifiableList(annotations.queueSubscriptions);
    }

    public List<PostgresNotificationMeta> getPostgresNotifications() {
        if (annotations.postgresNotifications == null)
            return Collections.emptyList();
        return Collections.unmodifiableList(annotations.postgresNotifications);
    }

    public boolean hasRestRoute() {
        return annotations.restRoute != null;
    }

    public boolean hasWebhook() {
        return annotations.webhook != null;
    }

    public boolean hasWebSocketRoute() {
        return annotations.webSocketRoute != null;
    }

    public boolean hasCalendarJobs() {
        return annotations.calendarJobs != null && !annotations.calendarJobs.isEmpty();
    }

    // ADDED
    public boolean hasQueueSubscriptions() {
        return annotations.queueSubscriptions != null && !annotations.queueSubscriptions.isEmpty();
    }

    public boolean hasPostgresNotifications() {
        return annotations.postgresNotifications != null && !annotations.postgresNotifications.isEmpty();
    }

    public boolean hasSecurity() {
        return annotations.secured != null || annotations.call != null;
    }

    public boolean hasMonitoring() {
        return annotations.monitored != null || annotations.perch != null;
    }

    public boolean hasAnnotations() {
        return hasRestRoute() ||
                hasWebhook() ||
                hasWebSocketRoute() ||
                hasCalendarJobs() ||
                hasQueueSubscriptions() || // ADDED
                hasPostgresNotifications() ||
                annotations.call != null ||
                annotations.perch != null ||
                annotations.databaseCorrelation != null ||
                hasSecurity() ||
                hasMonitoring();
    }

    // ---------------------------------------------------------------------
    // JSON support
    // ---------------------------------------------------------------------

    public static String toJson(ServiceCallDefinition def) {
        return GSON.toJson(def);
    }

    public static ServiceCallDefinition fromJson(String json) {
        return GSON.fromJson(json, ServiceCallDefinition.class);
    }

    // ---------------------------------------------------------------------
    // Construction from DB registry discovery
    // ---------------------------------------------------------------------

    public static ServiceCallDefinition fromDb(DbDiscoveredService svc) {
        return fromDb(svc.serviceType(), svc.database(), svc.schema(), svc.function(),
                svc.httpMethod(), svc.routePath(), svc.description(), svc.jsonType(), svc.annotations());
    }

    public static ServiceCallDefinition fromDb(String serviceType, String database, String schema, String function, 
                                               String httpMethod, String routePath, String description, String jsonType) {
        return fromDb(serviceType, database, schema, function, httpMethod, routePath, description, jsonType, null);
    }

    public static ServiceCallDefinition fromDb(String serviceType, String database, String schema, String function,
                                               String httpMethod, String routePath, String description, String jsonType,
                                               Annotations registryAnnotations) {
        ServiceCallDefinition def = new ServiceCallDefinition();

        def.setServiceType(serviceType);
        def.setClassName(null);
        def.setHandlerClass("GenericProcessHandler");
        def.setCategory("Database");
        def.setSource("db:" + database);
        def.setDbDatabase(database);
        def.setDbSchema(schema);
        def.setDbFunction(function);
        def.setActive(true);
        def.setJsonType(jsonType);

        Annotations ann = new Annotations();

        if (!isBlank(httpMethod) || !isBlank(routePath)) {
            RestRouteMeta route = new RestRouteMeta();
            route.method = httpMethod;
            route.path = routePath;
            ann.restRoute = route;
        }

        if (description != null && !description.isBlank()) {
            MonitoredMeta m = new MonitoredMeta();
            m.description = description;
            ann.monitored = m;
        }

        def.setAnnotations(mergeDbAnnotations(ann, registryAnnotations));
        return def;
    }

    public static ServiceCallDefinition fromRegistryHandler(String serviceType,
                                                            String handlerClass,
                                                            String category,
                                                            String source,
                                                            String jsonType,
                                                            String targetMethodName,
                                                            String httpMethod,
                                                            String routePath,
                                                            String description,
                                                            Annotations registryAnnotations) {
        ServiceCallDefinition def = new ServiceCallDefinition();

        def.setServiceType(serviceType);
        def.setClassName(handlerClass);
        def.setHandlerClass(handlerClass);
        def.setCategory(isBlank(category) ? "Generic" : category);
        def.setSource(isBlank(source) ? "registry" : source);
        def.setActive(true);
        def.setJsonType(jsonType);
        def.setTargetMethodName(targetMethodName);

        Annotations ann = new Annotations();

        if (!isBlank(httpMethod) || !isBlank(routePath)) {
            RestRouteMeta route = new RestRouteMeta();
            route.method = httpMethod;
            route.path = routePath;
            ann.restRoute = route;
        }

        if (!isBlank(description)) {
            MonitoredMeta m = new MonitoredMeta();
            m.description = description;
            ann.monitored = m;
        }

        def.setAnnotations(mergeDbAnnotations(ann, registryAnnotations));
        return def;
    }

    private static Annotations mergeDbAnnotations(Annotations base, Annotations override) {
        if (override == null) {
            return base;
        }

        Annotations merged = new Annotations();
        merged.call = mergeCall(base.call, override.call);
        merged.perch = mergePerch(base.perch, override.perch);
        merged.databaseCorrelation = override.databaseCorrelation != null
                ? override.databaseCorrelation
                : base.databaseCorrelation;
        merged.functionContract = override.functionContract != null
                ? override.functionContract
                : base.functionContract;
        merged.functionOverride = override.functionOverride != null
                ? override.functionOverride
                : base.functionOverride;
        merged.restRoute = base.restRoute != null ? base.restRoute : override.restRoute;
        merged.webhook = override.webhook != null ? override.webhook : base.webhook;
        merged.webSocketRoute = override.webSocketRoute != null ? override.webSocketRoute : base.webSocketRoute;
        merged.secured = override.secured != null ? override.secured : base.secured;
        merged.monitored = mergeMonitored(base.monitored, override.monitored);
        merged.calendarJobs = override.calendarJobs == null
                ? new ArrayList<>(base.calendarJobs)
                : new ArrayList<>(override.calendarJobs);
        merged.queueSubscriptions = override.queueSubscriptions == null
                ? new ArrayList<>(base.queueSubscriptions)
                : new ArrayList<>(override.queueSubscriptions);
        merged.postgresNotifications = override.postgresNotifications == null
                ? new ArrayList<>(base.postgresNotifications)
                : new ArrayList<>(override.postgresNotifications);
        return merged;
    }

    private static CallMeta mergeCall(CallMeta base, CallMeta override) {
        if (base == null && override == null) {
            return null;
        }

        CallMeta merged = new CallMeta();
        if (base != null) {
            merged.applicationKey = base.applicationKey;
            merged.open = base.open;
        }
        if (override != null) {
            if (!isBlank(override.applicationKey)) {
                merged.applicationKey = override.applicationKey;
            }
            merged.open = override.open;
        }
        return merged;
    }

    private static PerchMeta mergePerch(PerchMeta base, PerchMeta override) {
        if (base == null && override == null) {
            return null;
        }

        PerchMeta merged = new PerchMeta();
        if (base != null) {
            merged.applicationName = base.applicationName;
            merged.systemName = base.systemName;
            merged.description = base.description;
        }
        if (override != null) {
            if (!isBlank(override.applicationName)) {
                merged.applicationName = override.applicationName;
            }
            if (!isBlank(override.systemName)) {
                merged.systemName = override.systemName;
            }
            if (!isBlank(override.description)) {
                merged.description = override.description;
            }
        }
        return merged;
    }

    private static MonitoredMeta mergeMonitored(MonitoredMeta base, MonitoredMeta override) {
        if (base == null && override == null) {
            return null;
        }

        MonitoredMeta merged = new MonitoredMeta();
        if (override != null) {
            merged.application = override.application;
            merged.system = override.system;
            merged.description = override.description;
        }

        if (base != null) {
            if (isBlank(merged.application)) {
                merged.application = base.application;
            }
            if (isBlank(merged.system)) {
                merged.system = base.system;
            }
            if (isBlank(merged.description)) {
                merged.description = base.description;
            }
        }

        return merged;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ---------------------------------------------------------------------
    // Equality / String representation
    // ---------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ServiceCallDefinition that))
            return false;
        return Objects.equals(serviceType, that.serviceType)
                && Objects.equals(handlerClass, that.handlerClass)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceType, handlerClass, source);
    }

    public String getSummary() {
        return String.format(
                "%s [type=%s, category=%s, source=%s, active=%s]",
                handlerClass, serviceType, category, source, active);
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
