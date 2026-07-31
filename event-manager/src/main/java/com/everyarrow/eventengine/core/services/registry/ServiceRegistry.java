package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.RestRouteMeta;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.WebhookMeta;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition.WebSocketRouteMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of handlers and definitions.
 *
 * Holds:
 *  - serviceType -> handlers
 *  - serviceType -> definitions
 *  - REST route index (method + path)
 *  - Webhook route index (path)
 *  - WebSocket route index (path)
 *
 * This version supports templated REST routes like:
 *   /service-call-log/{id}
 * and returns extracted path params to the caller.
 */
public class ServiceRegistry {

    // serviceType -> candidate registrations. Definitions and handlers remain
    // paired so selecting a definition always selects its matching handler.
    private final Map<String, CopyOnWriteArrayList<RegisteredFunction>> registrations =
            new ConcurrentHashMap<>();

    // Protocol indexes derived from annotation metadata
    // REST key:    METHOD + " " + normalizedPath (only for exact, non-templated paths)
    // Webhook key: normalizedPath
    // WebSocket key: normalizedPath
    private final Map<String, ServiceCallDefinition> restIndex = new ConcurrentHashMap<>();
    private final Map<String, ServiceCallDefinition> webhookIndex = new ConcurrentHashMap<>();
    private final Map<String, ServiceCallDefinition> websocketIndex = new ConcurrentHashMap<>();

    // Templated REST routes grouped per HTTP method for matching with {vars}
    private final Map<String, CopyOnWriteArrayList<ServiceCallDefinition>> restTemplatesByMethod = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------

    public void register(String key, ServiceCallHandler handler, ServiceCallDefinition def) {
        register(key, handler, def, FunctionRegistrationOrigin.infer(def));
    }

    public synchronized void register(String key,
                                      ServiceCallHandler handler,
                                      ServiceCallDefinition def,
                                      FunctionRegistrationOrigin origin) {
        register(key, handler, def, origin, null);
    }

    public synchronized void register(String key,
                                      ServiceCallHandler handler,
                                      ServiceCallDefinition def,
                                      FunctionRegistrationOrigin origin,
                                      String ownerPluginId) {
        Objects.requireNonNull(key, "service type key is required");
        Objects.requireNonNull(handler, "handler is required");
        Objects.requireNonNull(def, "definition is required");
        Objects.requireNonNull(origin, "registration origin is required");

        registerCandidate(key, handler, def, origin, ownerPluginId);
    }

    /**
     * Register a definition only, for routing purposes.
     * Handler can be lazily created later by Engine.
     */
    public void registerDefinition(ServiceCallDefinition def) {
        registerDefinition(def, FunctionRegistrationOrigin.infer(def));
    }

    public synchronized void registerDefinition(ServiceCallDefinition def,
                                                FunctionRegistrationOrigin origin) {
        registerDefinition(def, origin, null);
    }

    public synchronized void registerDefinition(ServiceCallDefinition def,
                                                FunctionRegistrationOrigin origin,
                                                String ownerPluginId) {
        Objects.requireNonNull(def, "definition is required");
        Objects.requireNonNull(origin, "registration origin is required");
        String key = def.getServiceType();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("definition.serviceType is required");
        }

        registerCandidate(key, null, def, origin, ownerPluginId);
    }

    private void registerCandidate(String key,
                                   ServiceCallHandler handler,
                                   ServiceCallDefinition definition,
                                   FunctionRegistrationOrigin origin,
                                   String ownerPluginId) {
        String serviceType = definition.getServiceType();
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("definition.serviceType is required");
        }
        if (!key.equals(serviceType)) {
            throw new IllegalArgumentException(
                    "Registration key '" + key + "' must match definition.serviceType '"
                            + serviceType + "'");
        }

        FunctionCandidate candidate =
                FunctionCandidate.from(definition, origin, ownerPluginId);
        CopyOnWriteArrayList<RegisteredFunction> existing =
                registrations.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        RegisteredFunction replacement = new RegisteredFunction(candidate, definition, handler);

        int duplicateIndex = indexOfDeclaration(existing, candidate.declarationKey());
        if (duplicateIndex < 0) {
            // Plugin discovery may enrich the already-registered definition instance
            // with annotation metadata before its concrete handler is created lazily.
            // Treat that as a refresh of the original declaration, not a competing
            // implementation whose new metadata happens to produce a new signature.
            duplicateIndex = indexOfDefinitionIdentity(existing, definition);
        }
        if (duplicateIndex < 0) {
            // Database functions are discovered locally before the reconciled
            // database registry is loaded. Both registrations describe the same
            // executable function; the later registry projection is a metadata
            // refresh, not a competing implementation.
            duplicateIndex = indexOfDatabaseImplementation(existing, candidate);
        }
        List<FunctionCandidate> proposedCandidates = new ArrayList<>();
        for (int i = 0; i < existing.size(); i++) {
            if (i != duplicateIndex) {
                proposedCandidates.add(existing.get(i).candidate());
            }
        }
        proposedCandidates.add(candidate);

        // Resolve before mutating so an ambiguous registration is atomic.
        FunctionResolutionPolicy.resolve(serviceType, proposedCandidates);

        if (duplicateIndex >= 0) {
            RegisteredFunction duplicate = existing.get(duplicateIndex);
            ServiceCallHandler effectiveHandler = handler == null ? duplicate.handler() : handler;
            existing.set(duplicateIndex,
                    new RegisteredFunction(candidate, definition, effectiveHandler));
        } else {
            existing.add(replacement);
        }
        rebuildProtocolIndexes();
    }

    private int indexOfDeclaration(List<RegisteredFunction> registered,
                                   String declarationKey) {
        for (int i = 0; i < registered.size(); i++) {
            if (registered.get(i).candidate().declarationKey().equals(declarationKey)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfDefinitionIdentity(List<RegisteredFunction> registered,
                                          ServiceCallDefinition definition) {
        for (int i = 0; i < registered.size(); i++) {
            if (registered.get(i).definition() == definition) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfDatabaseImplementation(List<RegisteredFunction> registered,
                                              FunctionCandidate candidate) {
        if (candidate.kind() != FunctionCandidate.Kind.DB_FUNCTION) {
            return -1;
        }
        for (int i = 0; i < registered.size(); i++) {
            FunctionCandidate existing = registered.get(i).candidate();
            if (existing.kind() == FunctionCandidate.Kind.DB_FUNCTION
                    && existing.implementation().equals(candidate.implementation())) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------
    // Lookups
    // ---------------------------------------------------------------------

    public ServiceCallHandler get(String key) {
        RegisteredFunction selected = selectedRegistration(key);
        if (selected == null) {
            return null;
        }
        if (selected.handler() != null) {
            return selected.handler();
        }

        // A selected annotation definition may be lazily instantiated by the
        // pipeline. Returning an available lower-precedence generic handler
        // preserves that existing lazy-registration seam.
        return registrations.getOrDefault(key, new CopyOnWriteArrayList<>()).stream()
                .filter(registration -> registration.handler() != null)
                .sorted(Comparator.comparing(registration ->
                        registration.candidate().sortKey()))
                .map(RegisteredFunction::handler)
                .findFirst()
                .orElse(null);
    }

    public ServiceCallDefinition getDefinition(String serviceType) {
        RegisteredFunction selected = selectedRegistration(serviceType);
        return selected == null ? null : selected.definition();
    }

    public List<ServiceCallDefinition> getDefinitionsFor(String serviceType) {
        List<RegisteredFunction> registered = registrations.get(serviceType);
        if (registered == null || registered.isEmpty()) {
            return Collections.emptyList();
        }
        return registered.stream()
                .sorted(Comparator.comparing(registration ->
                        registration.candidate().sortKey()))
                .map(RegisteredFunction::definition)
                .toList();
    }

    public Collection<ServiceCallDefinition> listDefinitions() {
        List<ServiceCallDefinition> selected = new ArrayList<>();
        for (String serviceType : new TreeSet<>(registrations.keySet())) {
            ServiceCallDefinition definition = getDefinition(serviceType);
            if (definition != null) {
                selected.add(definition);
            }
        }
        return Collections.unmodifiableList(selected);
    }

    public Set<String> listServiceTypes() {
        return Collections.unmodifiableSet(new TreeSet<>(registrations.keySet()));
    }

    public FunctionResolution getResolution(String serviceType) {
        List<RegisteredFunction> registered = registrations.get(serviceType);
        if (registered == null || registered.isEmpty()) {
            return null;
        }
        return FunctionResolutionPolicy.resolve(
                serviceType,
                registered.stream().map(RegisteredFunction::candidate).toList());
    }

    public List<FunctionResolution> listResolutions() {
        List<FunctionResolution> resolutions = new ArrayList<>();
        for (String serviceType : new TreeSet<>(registrations.keySet())) {
            FunctionResolution resolution = getResolution(serviceType);
            if (resolution != null) {
                resolutions.add(resolution);
            }
        }
        return Collections.unmodifiableList(resolutions);
    }

    /**
     * Final startup/live-add gate. Registration itself permits an explicit
     * override to arrive before its generated target so discovery order cannot
     * affect the result; this gate requires every declared target to exist.
     */
    public void validateExplicitOverrides() {
        for (String serviceType : new TreeSet<>(registrations.keySet())) {
            List<RegisteredFunction> registered = registrations.get(serviceType);
            if (registered == null || registered.isEmpty()) {
                continue;
            }
            FunctionResolutionPolicy.resolveStrict(
                    serviceType,
                    registered.stream()
                            .map(RegisteredFunction::candidate)
                            .toList());
        }
    }

    private RegisteredFunction selectedRegistration(String serviceType) {
        List<RegisteredFunction> registered = registrations.get(serviceType);
        if (registered == null || registered.isEmpty()) {
            return null;
        }
        FunctionResolution resolution = FunctionResolutionPolicy.resolve(
                serviceType,
                registered.stream().map(RegisteredFunction::candidate).toList());
        String selectedKey = resolution.selected().declarationKey();
        return registered.stream()
                .filter(registration ->
                        registration.candidate().declarationKey().equals(selectedKey))
                .findFirst()
                .orElseThrow();
    }

    // ---------------------------------------------------------------------
    // Protocol indexes
    // ---------------------------------------------------------------------

    private void updateProtocolIndexes(ServiceCallDefinition def) {
        if (def == null || !def.isActive()) {
            return;
        }

        // REST index
        RestRouteMeta rest = def.getRestRoute();
        if (rest != null && rest.path != null && !rest.path.isBlank()) {
            String method = (rest.method == null || rest.method.isBlank())
                    ? "GET"
                    : rest.method.toUpperCase();
            String path = normalizePath(rest.path);

            if (isTemplatePath(path)) {
                restTemplatesByMethod
                        .computeIfAbsent(method, k -> new CopyOnWriteArrayList<>())
                        .add(def);
            } else {
                String key = method + " " + path;

                restIndex.putIfAbsent(key, def);
            }
        }

        // Webhook index
        WebhookMeta wh = def.getWebhook();
        if (wh != null && wh.path != null && !wh.path.isBlank()) {
            String path = normalizePath(wh.path);
            webhookIndex.putIfAbsent(path, def);
        }

        // WebSocket index
        WebSocketRouteMeta ws = def.getWebSocketRoute();
        if (ws != null && ws.path != null && !ws.path.isBlank()) {
            String path = normalizePath(ws.path);
            websocketIndex.putIfAbsent(path, def);
        }
    }

    private void rebuildProtocolIndexes() {
        restIndex.clear();
        webhookIndex.clear();
        websocketIndex.clear();
        restTemplatesByMethod.clear();

        for (ServiceCallDefinition definition : listDefinitions()) {
            updateProtocolIndexes(definition);
        }
    }

    private String normalizePath(String p) {
        if (p == null || p.isBlank()) return "/";
        p = p.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private boolean isTemplatePath(String p) {
        return p != null && p.contains("{") && p.contains("}");
    }

    // ---------------------------------------------------------------------
    // REST routing
    // ---------------------------------------------------------------------

    /**
     * Backwards-compatible exact-match method (no path params).
     */
    public ServiceCallDefinition findRestRoute(String httpMethod, String path) {
        RouteMatch m = findRestRouteMatch(httpMethod, path);
        return m == null ? null : m.definition();
    }

    /**
     * Finds a REST route match and extracts any template path params.
     * Supports:
     *   /foo/bar (exact)
     *   /foo/{id} (template)
     */
    public RouteMatch findRestRouteMatch(String httpMethod, String path) {
        String method = (httpMethod == null || httpMethod.isBlank())
                ? "GET"
                : httpMethod.toUpperCase();
        String p = normalizePath(path);

        // 1) Exact index hit
        String key = method + " " + p;
        ServiceCallDefinition def = restIndex.get(key);
        if (def != null && def.isActive()) {
            return new RouteMatch(def, Map.of());
        }

        // 2) Template match (method-scoped first, then fallback scan)
        List<ServiceCallDefinition> candidates = restTemplatesByMethod.get(method);
        if (candidates != null) {
            for (ServiceCallDefinition cand : candidates) {
                RouteMatch m = tryMatchTemplate(method, p, cand);
                if (m != null) return m;
            }
        }

        // 3) Last resort scan through all definitions
        for (ServiceCallDefinition d : listDefinitions()) {
            if (d == null || !d.isActive()) continue;
            RestRouteMeta rr = d.getRestRoute();
            if (rr == null || rr.path == null || rr.path.isBlank()) continue;

            String defMethod = (rr.method == null || rr.method.isBlank())
                    ? "GET"
                    : rr.method.toUpperCase();

            if (!defMethod.equals(method)) continue;

            String defPath = normalizePath(rr.path);

            if (!isTemplatePath(defPath)) {
                if (defPath.equals(p)) return new RouteMatch(d, Map.of());
                continue;
            }

            RouteMatch m = matchTemplate(defPath, p, d);
            if (m != null) return m;
        }

        return null;
    }

    private RouteMatch tryMatchTemplate(String method, String p, ServiceCallDefinition cand) {
        RestRouteMeta rr = cand.getRestRoute();
        if (rr == null || rr.path == null || rr.path.isBlank()) return null;

        String candMethod = (rr.method == null || rr.method.isBlank())
                ? "GET"
                : rr.method.toUpperCase();
        if (!candMethod.equals(method)) return null;

        String template = normalizePath(rr.path);
        if (!isTemplatePath(template)) return null;

        return matchTemplate(template, p, cand);
    }

    private RouteMatch matchTemplate(String template, String actualPath, ServiceCallDefinition def) {
        String[] tParts = splitPath(template);
        String[] aParts = splitPath(actualPath);

        if (tParts.length != aParts.length) return null;

        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < tParts.length; i++) {
            String t = tParts[i];
            String a = aParts[i];

            if (t.startsWith("{") && t.endsWith("}") && t.length() > 2) {
                String name = t.substring(1, t.length() - 1).trim();
                if (name.isEmpty()) return null;
                params.put(name, a);
            } else {
                if (!t.equals(a)) return null;
            }
        }
        return new RouteMatch(def, params);
    }

    private String[] splitPath(String p) {
        String n = normalizePath(p);
        if ("/".equals(n)) return new String[0];
        if (n.startsWith("/")) n = n.substring(1);
        return n.split("/");
    }

    // ---------------------------------------------------------------------
    // Webhook routing
    // ---------------------------------------------------------------------

    public ServiceCallDefinition findWebhook(String path) {
        String p = normalizePath(path);

        ServiceCallDefinition def = webhookIndex.get(p);
        if (def != null && def.isActive()) {
            return def;
        }

        for (ServiceCallDefinition d : listDefinitions()) {
            if (d == null || !d.isActive()) continue;
            WebhookMeta wh = d.getWebhook();
            if (wh == null || wh.path == null || wh.path.isBlank()) continue;
            String defPath = normalizePath(wh.path);
            if (defPath.equals(p)) {
                return d;
            }
        }

        return null;
    }

    public ServiceCallDefinition findWebSocket(String path) {
        String p = normalizePath(path);

        ServiceCallDefinition def = websocketIndex.get(p);
        if (def != null && def.isActive()) {
            return def;
        }

        for (ServiceCallDefinition d : listDefinitions()) {
            if (d == null || !d.isActive()) continue;
            WebSocketRouteMeta ws = d.getWebSocketRoute();
            if (ws == null || ws.path == null || ws.path.isBlank()) continue;
            String defPath = normalizePath(ws.path);
            if (defPath.equals(p)) {
                return d;
            }
        }

        return null;
    }

    // ---------------------------------------------------------------------
    // Removal
    // ---------------------------------------------------------------------

    public synchronized void deregisterServiceType(String serviceType) {
        registrations.remove(serviceType);
        rebuildProtocolIndexes();
    }

    public synchronized void deregisterSource(String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        removeCandidates(candidate -> source.equals(candidate.source()));
    }

    public synchronized void deregisterPlugin(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return;
        }
        removeCandidates(candidate -> pluginId.equals(candidate.pluginId()));
    }

    private void removeCandidates(java.util.function.Predicate<FunctionCandidate> predicate) {
        for (Map.Entry<String, CopyOnWriteArrayList<RegisteredFunction>> entry
                : registrations.entrySet()) {
            entry.getValue().removeIf(registration ->
                    predicate.test(registration.candidate()));
            if (entry.getValue().isEmpty()) {
                registrations.remove(entry.getKey(), entry.getValue());
            }
        }
        rebuildProtocolIndexes();
    }

    public synchronized void removeHandler(String serviceType, ServiceCallHandler handler) {
        CopyOnWriteArrayList<RegisteredFunction> registered = registrations.get(serviceType);
        if (registered != null) {
            registered.removeIf(registration -> registration.handler() == handler);
            if (registered.isEmpty()) {
                registrations.remove(serviceType);
            }
        }
        rebuildProtocolIndexes();
    }

    private record RegisteredFunction(
            FunctionCandidate candidate,
            ServiceCallDefinition definition,
            ServiceCallHandler handler) {
    }
}
