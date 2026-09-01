package com.singlepoint.security;

import com.singlepoint.user.domain.Role;

import java.util.UUID;

/** Authenticated caller, carried as the Spring Security principal. */
public class AppPrincipal {

    private final UUID userId;
    private final Role role;
    private final UUID tenantId;   // active tenant membership; null for a user with no active membership / super admin
    private final String name;

    public AppPrincipal(UUID userId, Role role, UUID tenantId, String name) {
        this.userId = userId;
        this.role = role;
        this.tenantId = tenantId;
        this.name = name;
    }

    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }

    public boolean isSuperAdmin() { return role == Role.SUPER_ADMIN; }
    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isProvider() { return role == Role.PROVIDER; }
    public boolean isResident() { return role == Role.RESIDENT; }
}
