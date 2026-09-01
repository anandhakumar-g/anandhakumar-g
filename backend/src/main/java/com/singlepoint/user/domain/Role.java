package com.singlepoint.user.domain;

/** Principal role. Stored lowercase in {@code app_user.role}. */
public enum Role {
    RESIDENT, ADMIN, PROVIDER, SUPER_ADMIN;

    public String db() { return name().toLowerCase(); }

    public static Role fromDb(String v) { return Role.valueOf(v.toUpperCase()); }

    public String authority() { return "ROLE_" + name(); }
}
