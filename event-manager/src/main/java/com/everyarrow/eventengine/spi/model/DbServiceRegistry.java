package com.everyarrow.eventengine.spi.model;

import java.util.*;

public class DbServiceRegistry {

    private final Map<String, DbDiscoveredService> services = new LinkedHashMap<>();

    public void add(DbDiscoveredService svc) {
        services.put(svc.serviceType(), svc);
    }

    public Optional<DbDiscoveredService> get(String serviceType) {
        return Optional.ofNullable(services.get(serviceType));
    }

    public Collection<DbDiscoveredService> all() {
        return services.values();
    }

    public int size() {
        return services.size();
    }

    public void clear() {
        services.clear();
    }
}
