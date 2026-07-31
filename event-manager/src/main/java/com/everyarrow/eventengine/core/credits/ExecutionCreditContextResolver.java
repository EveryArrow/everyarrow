package com.everyarrow.eventengine.core.credits;

import com.everyarrow.eventengine.core.hooks.SecurityHook;
import com.everyarrow.eventengine.core.services.runtime.InternalCallMetadata;
import com.everyarrow.eventengine.core.services.runtime.SecurityContext;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;
import com.everyarrow.utilities.environment.Environment;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves the credit identity and billable/non-billable classification for a ServiceCall.
 */
public final class ExecutionCreditContextResolver {
    public static final String CUSTOMER_ID_CONFIG = "EA_EXECUTION_CUSTOMER_ID";
    public static final String LICENSE_ID_CONFIG = "EA_EXECUTION_LICENSE_ID";
    public static final String INSTALLATION_ID_CONFIG = "EA_EXECUTION_INSTALLATION_ID";

    private final Environment environment;

    public ExecutionCreditContextResolver() {
        this(Environment.getInstance());
    }

    public ExecutionCreditContextResolver(Environment environment) {
        this.environment = environment == null ? Environment.getInstance() : environment;
    }

    public ExecutionCreditRequest requestFor(ServiceCall call) {
        ExecutionCreditContext context = contextFor(call);
        return new ExecutionCreditRequest(
                ExecutionType.fromServiceCallSource(call == null ? null : call.getSource()),
                call == null ? null : call.getType(),
                context.customerId(),
                context.licenseId(),
                context.installationId(),
                exclusionReasons(call));
    }

    public ExecutionCreditContext contextFor(ServiceCall call) {
        SecurityContext securityContext = call == null ? null : call.getSecurityContext();

        return new ExecutionCreditContext(
                firstNonBlank(securityContext == null ? null : securityContext.getCustomerId(),
                        metadataString(call, "credit.customerId"),
                        metadataString(call, "customer_id"),
                        paramString(call, "customer_id"),
                        envString(CUSTOMER_ID_CONFIG)),
                firstNonBlank(metadataString(call, "license.id"),
                        metadataString(call, "license_id"),
                        paramString(call, "license_id"),
                        envString(LICENSE_ID_CONFIG)),
                firstNonBlank(metadataString(call, "installation.id"),
                        metadataString(call, "installation_id"),
                        paramString(call, "installation_id"),
                        envString(INSTALLATION_ID_CONFIG)));
    }

    Set<ExecutionCreditExclusionReason> exclusionReasons(ServiceCall call) {
        Set<ExecutionCreditExclusionReason> reasons = new LinkedHashSet<>();
        if (metadataBoolean(call, SecurityHook.INTERNAL_SECURITY_CALL_METADATA)) {
            reasons.add(ExecutionCreditExclusionReason.INTERNAL_SECURITY_CALL);
        }
        if (metadataBoolean(call, InternalCallMetadata.PERCH_LIFECYCLE_CALL)) {
            reasons.add(ExecutionCreditExclusionReason.INTERNAL_PERCH_CALL);
        }

        String serviceType = lowerCase(call == null ? null : call.getType());
        if (serviceType == null) {
            return reasons;
        }
        if (serviceType.contains("health")) {
            reasons.add(ExecutionCreditExclusionReason.HEALTH_CHECK);
        }
        if (serviceType.contains("readiness") || serviceType.contains("ready")) {
            reasons.add(ExecutionCreditExclusionReason.READINESS_CHECK);
        }
        if (serviceType.contains("license")) {
            reasons.add(ExecutionCreditExclusionReason.LICENSE_CHECK);
        }
        if (serviceType.contains("credit") || serviceType.contains("usage")) {
            reasons.add(ExecutionCreditExclusionReason.CREDIT_OR_USAGE_CALL);
        }
        if (serviceType.contains("attestation")) {
            reasons.add(ExecutionCreditExclusionReason.ATTESTATION_CALL);
        }
        return reasons;
    }

    private boolean metadataBoolean(ServiceCall call, String key) {
        Object value = call == null ? null : call.getMetadata(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(stringValue(value));
    }

    private String metadataString(ServiceCall call, String key) {
        return call == null ? null : stringValue(call.getMetadata(key));
    }

    private String paramString(ServiceCall call, String key) {
        if (call == null || call.getParams() == null || key == null
                || !call.getParams().has(key) || call.getParams().get(key).isJsonNull()) {
            return null;
        }
        try {
            return trimToNull(call.getParams().get(key).getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String envString(String key) {
        return key == null ? null : trimToNull(environment.get(key, ""));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : trimToNull(value.toString());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String lowerCase(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
