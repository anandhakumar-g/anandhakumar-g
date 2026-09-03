package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-7 (A): one admin account administers several communities and switches the active one. */
class AdminMultiCommunityIT extends IntegrationTestBase {

    private String su;
    private UUID tenantA;
    private UUID tenantB;
    private String adminUserId;
    private String adminToken;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenantA = createTenant(su, "Green Meadows", "#2E7D32");
        tenantB = createTenant(su, "Lakeview", "#1565C0");
        adminUserId = createAdmin(su, tenantA, "+919000000101", "Shared Admin");
        attachAdmin(su, tenantB, adminUserId);
        adminToken = login("+919000000101").token();
    }

    @Test
    void meListsBothCommunitiesAndAIsActive() {
        JsonNode me = get("/api/v1/me", adminToken);
        assertEquals(2, me.get("memberships").size());
        assertEquals(tenantA.toString(), me.get("activeTenantId").asText());
        for (JsonNode m : me.get("memberships")) {
            assertEquals("ADMIN", m.get("relation").asText());
        }
    }

    @Test
    void switchingScopesAdminWritesToTheChosenCommunity() {
        String bToken = post("/api/v1/me/active-community", adminToken, Map.of("tenantId", tenantB.toString()))
                .get("token").asText();
        assertEquals(tenantB.toString(),
                get("/api/v1/me", bToken).get("activeTenantId").asText());

        // a flat created while scoped to B is not visible from A
        String flatB = post("/api/v1/admin/flats", bToken, Map.of("block", "B", "flatNumber", "1"))
                .get("id").asText();
        String aToken = post("/api/v1/me/active-community", adminToken, Map.of("tenantId", tenantA.toString()))
                .get("token").asText();
        for (JsonNode f : get("/api/v1/admin/flats", aToken)) {
            assertNotEquals(flatB, f.get("id").asText());
        }
    }

    @Test
    void detachingFromTheActiveCommunityFallsBackToTheOther() {
        // make A active explicitly, then detach A
        post("/api/v1/me/active-community", adminToken, Map.of("tenantId", tenantA.toString()));
        var r = http(HttpMethod.DELETE,
                "/api/v1/superadmin/tenants/" + tenantA + "/admins/" + adminUserId, su, null);
        assertEquals(204, r.getStatusCode().value());

        JsonNode me = get("/api/v1/me", login("+919000000101").token());
        assertEquals(1, me.get("memberships").size());
        assertEquals(tenantB.toString(), me.get("activeTenantId").asText());
    }

    @Test
    void adminSeatsCountsAdminTenantRows() {
        String bToken = post("/api/v1/me/active-community", adminToken, Map.of("tenantId", tenantB.toString()))
                .get("token").asText();
        long adminSeats = -1;
        for (JsonNode u : get("/api/v1/me/billing", bToken).get("usage")) {
            if (u.get("feature").asText().equals("ADMIN_SEATS")) adminSeats = u.get("used").asLong();
        }
        assertEquals(1, adminSeats);
    }
}
