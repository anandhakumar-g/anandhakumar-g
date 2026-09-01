package com.singlepoint.security;

import java.util.UUID;

/**
 * Per-request holder for the tenant scope that must be applied to the DB session
 * ({@code SET app.current_tenant_id}). Value is either a tenant UUID string, the
 * wildcard {@code *} (Super Admin, cross-tenant) or {@code null} (fail closed).
 */
public final class TenantContext {

    public static final String WILDCARD = "*";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() { }

    public static void setTenant(UUID tenantId) {
        CURRENT.set(tenantId != null ? tenantId.toString() : null);
    }

    public static void setWildcard() {
        CURRENT.set(WILDCARD);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
