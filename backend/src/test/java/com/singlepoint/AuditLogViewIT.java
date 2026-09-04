package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-9 (A): the Super Admin audit-log viewer over the rows AuditAspect already writes. */
class AuditLogViewIT extends IntegrationTestBase {

    private String superToken;
    private UUID tenantId;
    private String adminToken;

    @BeforeEach
    void fixture() {
        superToken = login(SUPER_ADMIN_PHONE).token();
        tenantId = createTenant(superToken, "Green Meadows", "#2E7D32");
        createAdmin(superToken, tenantId, "+919000000101", "Green Admin");
        adminToken = login("+919000000101").token();
    }

    @Test
    void unfilteredIsNewestFirstAndActionSubstringMatches() {
        createInvite(adminToken); // AdminInviteController#... -> a fresh audit row

        JsonNode page = get("/api/v1/superadmin/audit-logs", superToken);
        JsonNode rows = page.get("content");
        assertTrue(rows.size() >= 3, "expected create-tenant + create-admin + invite rows");

        // newest first
        java.time.Instant prev = null;
        for (JsonNode r : rows) {
            java.time.Instant at = java.time.Instant.parse(r.get("at").asText());
            if (prev != null) assertFalse(at.isAfter(prev), "rows must be created_at DESC");
            prev = at;
        }

        JsonNode invites = get("/api/v1/superadmin/audit-logs?action=Invite", superToken).get("content");
        assertTrue(invites.size() >= 1);
        for (JsonNode r : invites) {
            assertTrue(r.get("action").asText().toLowerCase().contains("invite"), r.get("action").asText());
        }
    }

    @Test
    void failedCallsAreRecordedAndFilterable() {
        // fails inside the controller (unknown tenant) -> audited as success=false / SP-404
        assertEquals(404, http(HttpMethod.POST,
                "/api/v1/superadmin/tenants/" + UUID.randomUUID() + "/admins", superToken,
                Map.of("phone", "+919000000999", "name", "Nobody")).getStatusCode().value());

        JsonNode failed = get("/api/v1/superadmin/audit-logs?success=false", superToken).get("content");
        assertTrue(failed.size() >= 1);
        boolean sawNotFound = false;
        for (JsonNode r : failed) {
            assertFalse(r.get("success").asBoolean());
            assertEquals("POST", r.get("httpMethod").asText());
            if ("SP-404".equals(r.path("errorCode").asText())) sawNotFound = true;
        }
        assertTrue(sawNotFound, "the failed create-admin call should carry errorCode SP-404");
    }

    @Test
    void tenantScopeFilterOnlyReturnsThatCommunitysRows() {
        createInvite(adminToken); // tenant-scoped: the admin's principal carries tenantId

        JsonNode scoped = get("/api/v1/superadmin/audit-logs?tenantId=" + tenantId, superToken).get("content");
        assertTrue(scoped.size() >= 1);
        for (JsonNode r : scoped) {
            assertEquals(tenantId.toString(), r.get("tenantId").asText());
        }
    }

    @Test
    void onlySuperAdminMayRead_andActionsListsDistinctValues() {
        assertEquals(403, http(HttpMethod.GET, "/api/v1/superadmin/audit-logs", adminToken, null)
                .getStatusCode().value());

        String resident = joinResident(tenantId, adminToken, "+919888000041", "Rita");
        assertEquals(403, http(HttpMethod.GET, "/api/v1/superadmin/audit-logs", resident, null)
                .getStatusCode().value());

        JsonNode actions = get("/api/v1/superadmin/audit-logs/actions", superToken);
        assertTrue(actions.isArray() && actions.size() >= 1);
    }
}
