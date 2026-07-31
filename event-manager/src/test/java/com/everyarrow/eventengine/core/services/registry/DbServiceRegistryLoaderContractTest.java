package com.everyarrow.eventengine.core.services.registry;

import com.everyarrow.eventengine.spi.model.DbDiscoveredService;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbServiceRegistryLoaderContractTest {

    @Test
    void generatedServiceCallOnlyRowDoesNotCreateRestRoute() {
        ServiceCallDefinition definition = ServiceCallDefinition.fromDb(new DbDiscoveredService(
                "EveryArrow",
                "app_security",
                "principal_select_one",
                "EveryArrow.app_security.principal_select_one",
                null,
                null,
                "Generated principal lookup",
                null,
                "json",
                null));

        DbServiceRegistrationPolicy.validate(definition, "event_manager.registry_functions");

        assertFalse(definition.hasRestRoute());
        assertTrue(definition.getSource().startsWith("db:"));
    }

    @Test
    void routeMetadataWithoutApplicationMetadataIsRejected() {
        ServiceCallDefinition definition = ServiceCallDefinition.fromDb(new DbDiscoveredService(
                "EveryArrow",
                "app_security",
                "principal_select_one",
                "EveryArrow.app_security.principal_select_one",
                "GET",
                "/db/app-security/principal/{id}",
                "Generated principal lookup",
                null,
                "json",
                null));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DbServiceRegistrationPolicy.validate(definition, "event_manager.registry_functions"));
        assertTrue(error.getMessage().contains("must declare annotations.call.applicationKey"));
    }
}
