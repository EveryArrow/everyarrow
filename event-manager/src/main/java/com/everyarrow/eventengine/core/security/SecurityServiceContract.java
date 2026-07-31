/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.security;

/**
 * Built-in access-control contract for the app_security schema.
 */
public final class SecurityServiceContract {

    public static final String APPLICATION_KEY = "SECURITY";
    public static final String DATABASE = EveryArrowServicePrefixes.DATABASE;
    public static final String SCHEMA = EveryArrowServicePrefixes.APP_SECURITY_SCHEMA;
    public static final String SERVICE_PREFIX = EveryArrowServicePrefixes.SECURITY_ACCESS_CONTROL;
    public static final String CACHE_INVALIDATION_CHANNEL = "app_security_changed";

    public static final String EVERYARROW_ADMIN_ROLE_KEY = "EVERYARROW_ADMIN";

    public static final String HEALTH_CHECK = SERVICE_PREFIX + ".health_check";
    public static final String RESOLVE_PRINCIPAL = SERVICE_PREFIX + ".resolve_principal";
    public static final String RESOLVE_CUSTOMER_SCOPE = SERVICE_PREFIX + ".resolve_customer_scope";
    public static final String API_KEY_CREATE = SERVICE_PREFIX + ".api_key_create";
    public static final String API_KEY_LIST_BY_PRINCIPAL = SERVICE_PREFIX + ".api_key_list_by_principal";
    public static final String API_KEY_SELECT_BY_HASH = SERVICE_PREFIX + ".api_key_select_by_hash";
    public static final String API_KEY_DELETE = SERVICE_PREFIX + ".api_key_delete";
    public static final String AUTHORIZE_SERVICE_CALL = SERVICE_PREFIX + ".authorize_service_call";
    public static final String CHECK_EVERYARROW_ADMIN_ACCESS = SERVICE_PREFIX + ".check_everyarrow_admin_access";
    public static final String CHECK_API_KEY_ACCESS = SERVICE_PREFIX + ".check_api_key_access";
    public static final String CHECK_USER_ACCESS = SERVICE_PREFIX + ".check_user_access";
    public static final String CACHE_INVALIDATE = SERVICE_PREFIX + ".cache_invalidate";
    public static final String CUSTOMER_CREATE = SERVICE_PREFIX + ".customer_create";
    public static final String CUSTOMER_HAS_ONBOARDED = SERVICE_PREFIX + ".customer_has_onboarded";
    public static final String PRINCIPAL_SELECT_BY_CUSTOMER_ID =
            SERVICE_PREFIX + ".principal_select_by_customer_id";
    public static final String PRINCIPAL_CREATE = SERVICE_PREFIX + ".principal_create";
    public static final String PRINCIPAL_UPDATE = SERVICE_PREFIX + ".principal_update";
    public static final String PRINCIPAL_DELETE = SERVICE_PREFIX + ".principal_delete";
    public static final String ROLE_TEMPLATE_SELECT_ALL = SERVICE_PREFIX + ".role_template_select_all";
    public static final String ROLE_SELECT_BY_CUSTOMER_ID = SERVICE_PREFIX + ".role_select_by_customer_id";
    public static final String ROLE_CREATE = SERVICE_PREFIX + ".role_create";
    public static final String ROLE_UPDATE = SERVICE_PREFIX + ".role_update";
    public static final String ROLE_DELETE = SERVICE_PREFIX + ".role_delete";
    public static final String PRINCIPAL_ROLE_ASSIGNMENT_SELECT_BY_PRINCIPAL =
            SERVICE_PREFIX + ".principal_role_assignment_select_by_principal";
    public static final String PRINCIPAL_ROLE_ASSIGNMENT_INSERT =
            SERVICE_PREFIX + ".principal_role_assignment_insert";
    public static final String PRINCIPAL_ROLE_ASSIGNMENT_DELETE =
            SERVICE_PREFIX + ".principal_role_assignment_delete";

    private SecurityServiceContract() {
    }
}
