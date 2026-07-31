package com.everyarrow.eventengine.core.services.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Trusted per-call security state assembled by the engine before handler execution.
 *
 * This starts as a minimal carrier for target metadata and credential hints so the
 * runtime has one place to evolve toward full authentication and authorization.
 */
public class SecurityContext {

    public enum CredentialType {
        NONE,
        BEARER,
        API_KEY
    }

    private CredentialType credentialType = CredentialType.NONE;
    private String serviceType;
    private String definitionSource;
    private String targetApplication;
    private String targetConsoleId;
    private String targetSystem;
    private ServiceCallSource callSource = ServiceCallSource.INTERNAL;
    private String authProviderId;
    private String email;
    private String principalId;
    private String clientId;
    private String customerId;
    private String customerPrincipalId;
    private String consoleId;
    private boolean authenticated;
    private boolean systemCall;
    private boolean publicRoute;
    private final Set<String> roles = new LinkedHashSet<>();
    private final Set<String> permissions = new LinkedHashSet<>();

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType == null ? CredentialType.NONE : credentialType;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getDefinitionSource() {
        return definitionSource;
    }

    public void setDefinitionSource(String definitionSource) {
        this.definitionSource = definitionSource;
    }

    public String getTargetApplication() {
        return targetApplication;
    }

    public void setTargetApplication(String targetApplication) {
        this.targetApplication = targetApplication;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public String getTargetConsoleId() {
        return targetConsoleId;
    }

    public void setTargetConsoleId(String targetConsoleId) {
        this.targetConsoleId = targetConsoleId;
    }

    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    public ServiceCallSource getCallSource() {
        return callSource;
    }

    public void setCallSource(ServiceCallSource callSource) {
        this.callSource = callSource == null ? ServiceCallSource.INTERNAL : callSource;
    }

    public String getAuthProviderId() {
        return authProviderId;
    }

    public void setAuthProviderId(String authProviderId) {
        this.authProviderId = authProviderId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerPrincipalId() {
        return customerPrincipalId;
    }

    public void setCustomerPrincipalId(String customerPrincipalId) {
        this.customerPrincipalId = customerPrincipalId;
    }

    public String getConsoleId() {
        return consoleId;
    }

    public void setConsoleId(String consoleId) {
        this.consoleId = consoleId;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isSystemCall() {
        return systemCall;
    }

    public void setSystemCall(boolean systemCall) {
        this.systemCall = systemCall;
    }

    public boolean isPublicRoute() {
        return publicRoute;
    }

    public void setPublicRoute(boolean publicRoute) {
        this.publicRoute = publicRoute;
    }

    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void addRole(String role) {
        if (role != null && !role.isBlank()) {
            roles.add(role);
        }
    }

    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public void addPermission(String permission) {
        if (permission != null && !permission.isBlank()) {
            permissions.add(permission);
        }
    }

    public SecurityContext copy() {
        SecurityContext copy = new SecurityContext();
        copy.setCredentialType(credentialType);
        copy.setServiceType(serviceType);
        copy.setDefinitionSource(definitionSource);
        copy.setTargetApplication(targetApplication);
        copy.setTargetConsoleId(targetConsoleId);
        copy.setTargetSystem(targetSystem);
        copy.setCallSource(callSource);
        copy.setAuthProviderId(authProviderId);
        copy.setEmail(email);
        copy.setPrincipalId(principalId);
        copy.setClientId(clientId);
        copy.setCustomerId(customerId);
        copy.setCustomerPrincipalId(customerPrincipalId);
        copy.setConsoleId(consoleId);
        copy.setAuthenticated(authenticated);
        copy.setSystemCall(systemCall);
        copy.setPublicRoute(publicRoute);
        roles.forEach(copy::addRole);
        permissions.forEach(copy::addPermission);
        return copy;
    }

    public static SecurityContext copyOf(SecurityContext original) {
        return original == null ? null : original.copy();
    }
}
