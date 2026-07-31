package com.everyarrow.eventengine.core.services.credits;

import com.everyarrow.eventengine.core.credits.ExecutionCreditServiceContract;
import com.everyarrow.eventengine.core.credits.ExecutionCreditStatusService;
import com.everyarrow.eventengine.core.credits.ExecutionLicenseImportService;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.eventengine.spi.annotations.RestRoute;
import com.everyarrow.eventengine.spi.annotations.Secured;
import com.everyarrow.eventengine.spi.annotations.ServiceCallName;
import com.everyarrow.eventengine.spi.handler.ServiceCallHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Event Manager-owned execution-credit status endpoints for console surfaces.
 */
public class ExecutionCreditStatusHandler implements ServiceCallHandler {

    private final ExecutionCreditStatusService statusService;
    private final ExecutionLicenseImportService importService;

    public ExecutionCreditStatusHandler() {
        this(new ExecutionCreditStatusService(), new ExecutionLicenseImportService());
    }

    ExecutionCreditStatusHandler(ExecutionCreditStatusService statusService) {
        this(statusService, new ExecutionLicenseImportService());
    }

    public ExecutionCreditStatusHandler(ExecutionCreditStatusService statusService,
                                        ExecutionLicenseImportService importService) {
        if (statusService == null) {
            throw new IllegalArgumentException("statusService is required");
        }
        if (importService == null) {
            throw new IllegalArgumentException("importService is required");
        }
        this.statusService = statusService;
        this.importService = importService;
    }

    @ServiceCallName(
            value = ExecutionCreditServiceContract.STATUS,
            applicationKey = ExecutionCreditServiceContract.APPLICATION_KEY)
    @Secured(roles = {"system", "admin"}, scopes = {"execution_credits:read"})
    @RestRoute(
            path = "/execution-credits/status",
            method = "GET",
            applicationKey = ExecutionCreditServiceContract.APPLICATION_KEY)
    public JsonElement status(ServiceCall call) {
        return statusService.status(call);
    }

    @ServiceCallName(
            value = ExecutionCreditServiceContract.USAGE_SUMMARY,
            applicationKey = ExecutionCreditServiceContract.APPLICATION_KEY)
    @Secured(roles = {"system", "admin"}, scopes = {"execution_credits:read"})
    @RestRoute(
            path = "/execution-credits/usage",
            method = "GET",
            applicationKey = ExecutionCreditServiceContract.APPLICATION_KEY)
    public JsonElement usageSummary(ServiceCall call) {
        return statusService.usageSummary(call);
    }

    @ServiceCallName(
            value = ExecutionCreditServiceContract.LICENSE_IMPORT,
            applicationKey = ExecutionCreditServiceContract.APPLICATION_KEY)
    @Secured(roles = {"system", "admin"}, scopes = {"execution_licenses:write"})
    @RestRoute(
            path = "/execution-licenses/import",
            method = "POST",
            applicationKey = ExecutionCreditServiceContract.APPLICATION_KEY)
    public JsonElement importLicense(ServiceCall call) {
        return importService.importLicense(call);
    }

    @Override
    public JsonElement execute(JsonObject params, String type, String source) {
        JsonObject error = new JsonObject();
        error.addProperty("error", "Direct execute() is not supported; use a named execution-credit service type.");
        return error;
    }
}
