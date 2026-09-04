package com.singlepoint.security;

import java.util.UUID;

/**
 * MVP-8: per-request holder for the signed-in user id, applied to the DB session as
 * {@code SET app.current_user_id}. Used by the RLS null-tenant branch so a community-less
 * individual (and the provider they book) can see their own tenant-less {@code ticket} rows.
 * Empty for unauthenticated / system connections.
 */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private UserContext() { }

    public static void set(UUID userId) {
        CURRENT.set(userId != null ? userId.toString() : null);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
