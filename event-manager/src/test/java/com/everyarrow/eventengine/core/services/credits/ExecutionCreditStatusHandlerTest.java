package com.everyarrow.eventengine.core.services.credits;

import com.everyarrow.eventengine.core.adapters.AnnotationDefinitionBuilder;
import com.everyarrow.eventengine.core.credits.ExecutionCreditServiceContract;
import com.everyarrow.eventengine.core.services.registry.ServiceRegistry;
import com.everyarrow.eventengine.spi.model.ServiceCallDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionCreditStatusHandlerTest {

    @Test
    void annotatedRestDefinitionsResolveForStatusUsageAndImportRoutes() {
        ServiceRegistry registry = new ServiceRegistry();
        List<ServiceCallDefinition> definitions =
                AnnotationDefinitionBuilder.buildDefinitions(ExecutionCreditStatusHandler.class);
        definitions.forEach(registry::registerDefinition);

        ServiceCallDefinition status = registry.findRestRoute("GET", "/execution-credits/status");
        ServiceCallDefinition usage = registry.findRestRoute("GET", "/execution-credits/usage");
        ServiceCallDefinition licenseImport = registry.findRestRoute("POST", "/execution-licenses/import");

        assertNotNull(status);
        assertNotNull(usage);
        assertNotNull(licenseImport);
        assertEquals(ExecutionCreditServiceContract.STATUS, status.getServiceType());
        assertEquals(ExecutionCreditServiceContract.USAGE_SUMMARY, usage.getServiceType());
        assertEquals(ExecutionCreditServiceContract.LICENSE_IMPORT, licenseImport.getServiceType());
        assertEquals(ExecutionCreditServiceContract.APPLICATION_KEY,
                status.getAnnotations().call.applicationKey);
        assertEquals(ExecutionCreditServiceContract.APPLICATION_KEY,
                usage.getAnnotations().call.applicationKey);
        assertEquals(ExecutionCreditServiceContract.APPLICATION_KEY,
                licenseImport.getAnnotations().call.applicationKey);
        assertTrue(status.getAnnotations().secured.scopes.contains("execution_credits:read"));
        assertTrue(usage.getAnnotations().secured.scopes.contains("execution_credits:read"));
        assertTrue(licenseImport.getAnnotations().secured.scopes.contains("execution_licenses:write"));
    }
}
