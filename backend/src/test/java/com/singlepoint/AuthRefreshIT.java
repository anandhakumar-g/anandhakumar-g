package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-6 (A): POST /auth/refresh re-mints the caller's session — fixes the post-approval stale-token gap. */
class AuthRefreshIT extends IntegrationTestBase {

    private String su;
    private UUID tenant;
    private String adminToken;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenant = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenant, "+919000000101", "GM Admin");
        adminToken = login("+919000000101").token();
    }

    @Test
    void approvedResidentPicksUpTheTenantScopeViaRefresh() {
        // resident requests to join with no invite code → PENDING_APPROVAL, token has no tenant
        String tok = completeProfile(login("+919888000010").token(), "Ravi", "ravi@example.com").token();
        JsonNode joined = post("/api/v1/memberships/join", tok,
                Map.of("tenantId", tenant.toString(), "requestedFlatLabel", "A-101"));
        assertEquals("PENDING_APPROVAL", joined.get("onboardingState").asText());
        String pendingToken = joined.get("token").asText();
        assertEquals(403, http(HttpMethod.GET, "/api/v1/tickets", pendingToken, null).getStatusCode().value());

        // admin approves
        String membershipId = get("/api/v1/admin/join-requests", adminToken).get(0).get("id").asText();
        post("/api/v1/admin/join-requests/" + membershipId + "/approve", adminToken, Map.of());

        // resident refreshes with the OLD token → new token is tenant-scoped
        JsonNode refreshed = post("/api/v1/auth/refresh", pendingToken, Map.of());
        assertEquals("READY", refreshed.get("onboardingState").asText());
        assertEquals(tenant.toString(), refreshed.get("user").get("activeTenantId").asText());
        assertEquals(200, http(HttpMethod.GET, "/api/v1/tickets", refreshed.get("token").asText(), null)
                .getStatusCode().value());
    }

    @Test
    void refreshWithoutATokenIs401() {
        var r = http(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of());
        assertEquals(401, r.getStatusCode().value());
        assertEquals("SP-401", r.getBody().get("errorCode").asText());
    }
}
