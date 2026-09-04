package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.security.TenantContext;
import com.singlepoint.security.UserContext;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-8 (A): a tenant-less ticket is visible only to its raiser, its assigned provider, and the wildcard. */
class TenantlessRlsIT extends IntegrationTestBase {

    /** Seeded global "Electrical" category (see SEED_TAXONOMY_SQL). */
    private static final String GLOBAL_CATEGORY = "11111111-0000-0000-0000-000000000001";

    private Marketplace mk;
    private UUID raiserUserId;
    private UUID providerUserId;
    private UUID otherUserId;
    private UUID ticketId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");

        String raiser = completeProfile(login("+919888500001").token(), "Nomad", "nomad@example.com").token();
        raiserUserId = UUID.fromString(get("/api/v1/me", raiser).get("userId").asText());
        providerUserId = UUID.fromString(get("/api/v1/me", mk.providerToken()).get("userId").asText());
        String other = completeProfile(login("+919888500002").token(), "Stranger", "s@example.com").token();
        otherUserId = UUID.fromString(get("/api/v1/me", other).get("userId").asText());

        ticketId = UUID.randomUUID();
        TenantContext.setWildcard();
        try {
            jdbcTemplate.update(
                "insert into ticket (id, tenant_id, reference_code, raised_by_user_id, category_id, "
                + "status, request_mode, service_address_text, assigned_provider_id) "
                + "values (?::uuid, NULL, ?, ?::uuid, ?::uuid, 'ASSIGNED', 'DIRECT_SERVICE', 'nowhere', ?::uuid)",
                ticketId.toString(), "SP-TL-1", raiserUserId.toString(), GLOBAL_CATEGORY, mk.providerId().toString());
            jdbcTemplate.update(
                "insert into ticket_status_history (tenant_id, ticket_id, to_status, actor_role) "
                + "values (NULL, ?::uuid, 'ASSIGNED', 'RESIDENT')", ticketId.toString());
        } finally {
            TenantContext.clear();
        }
    }

    private long visibleCount(String sql) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, ticketId.toString());
        return n == null ? 0 : n;
    }

    private long ticketVisible() {
        return visibleCount("select count(*) from ticket where id = ?::uuid");
    }

    private long historyVisible() {
        return visibleCount("select count(*) from ticket_status_history where ticket_id = ?::uuid");
    }

    @Test
    void raiserAndAssignedProviderSeeIt_othersDoNot() {
        UserContext.set(raiserUserId);
        try { assertEquals(1, ticketVisible()); assertEquals(1, historyVisible()); } finally { UserContext.clear(); }

        UserContext.set(providerUserId);
        try { assertEquals(1, ticketVisible()); assertEquals(1, historyVisible()); } finally { UserContext.clear(); }

        UserContext.set(otherUserId);
        try { assertEquals(0, ticketVisible()); assertEquals(0, historyVisible()); } finally { UserContext.clear(); }
    }

    @Test
    void aTenantScopedThirdPartyCannotSeeATenantlessTicket() {
        // an unrelated user, even from inside a real community's scope, sees nothing
        TenantContext.setTenant(mk.tenantId());
        UserContext.set(otherUserId);
        try {
            assertEquals(0, ticketVisible());
            assertEquals(0, historyVisible());
        } finally {
            TenantContext.clear();
            UserContext.clear();
        }
    }

    @Test
    void wildcardSeesIt() {
        TenantContext.setWildcard();
        try {
            assertEquals(1, ticketVisible());
            assertEquals(1, historyVisible());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void tenantRowsAreUnaffectedByTheNullTenantBranch() {
        // a normal community ticket
        String residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888500010", "Ravi");
        String cat = firstCategoryId(residentToken, "Electrical");
        String tid = post("/api/v1/tickets", residentToken,
                java.util.Map.of("categoryId", cat, "description", "x", "serviceAddressText", "A-1")).get("id").asText();
        UUID residentUserId = UUID.fromString(get("/api/v1/me", residentToken).get("userId").asText());

        // its own raiser cannot see it from a tenant-less connection (the null-tenant branch must not leak tenant rows)
        UserContext.set(residentUserId);
        try {
            Long n = jdbcTemplate.queryForObject("select count(*) from ticket where id = ?::uuid", Long.class, tid);
            assertEquals(0L, n);
        } finally {
            UserContext.clear();
        }
        // and a different tenant's scope cannot see it
        UUID otherTenant = UUID.fromString(post("/api/v1/superadmin/tenants", mk.superToken(),
                java.util.Map.of("name", "Palm Grove", "city", "Bengaluru")).get("id").asText());
        TenantContext.setTenant(otherTenant);
        try {
            Long n = jdbcTemplate.queryForObject("select count(*) from ticket where id = ?::uuid", Long.class, tid);
            assertEquals(0L, n);
        } finally {
            TenantContext.clear();
        }
    }
}
