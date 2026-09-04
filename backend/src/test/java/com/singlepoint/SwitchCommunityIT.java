package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-6 (A): a resident in two communities can switch the active one and leave one. */
class SwitchCommunityIT extends IntegrationTestBase {

    private String su;
    private UUID tenantA;
    private String adminA;
    private UUID tenantB;
    private String adminB;
    private String residentToken;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenantA = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenantA, "+919000000101", "GM Admin");
        adminA = login("+919000000101").token();
        tenantB = createTenant(su, "Lakeview", "#1565C0");
        createAdmin(su, tenantB, "+919000000201", "LV Admin");
        adminB = login("+919000000201").token();

        residentToken = joinResident(tenantA, adminA, "+919888000010", "Ravi");
        residentToken = joinExistingResident(residentToken, tenantB, adminB);
    }

    private String activeTenant(String token) {
        JsonNode me = get("/api/v1/me", token);
        return me.hasNonNull("activeTenantId") ? me.get("activeTenantId").asText() : null;
    }

    @Test
    void twoMembershipsAndTheFirstStaysActive() {
        JsonNode me = get("/api/v1/me", residentToken);
        assertEquals(2, me.get("memberships").size());
        assertEquals(tenantA.toString(), me.get("activeTenantId").asText());
    }

    @Test
    void switchingScopesTheTokenAndTicketVisibilityFollows() {
        JsonNode s = post("/api/v1/me/active-community", residentToken, Map.of("tenantId", tenantB.toString()));
        assertEquals("READY", s.get("onboardingState").asText());
        String bToken = s.get("token").asText();
        assertEquals(tenantB.toString(), s.get("user").get("activeTenantId").asText());

        // raise a ticket while scoped to B
        String cat = firstCategoryId(bToken, "Electrical");
        String id = post("/api/v1/tickets", bToken, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "B-1")).get("id").asText();

        // switch back to A → that ticket is out of scope
        String aToken = post("/api/v1/me/active-community", residentToken, Map.of("tenantId", tenantA.toString()))
                .get("token").asText();
        var r = http(HttpMethod.GET, "/api/v1/tickets/" + id, aToken, null);
        assertEquals(404, r.getStatusCode().value());

        // switch to B again → visible
        String bAgain = post("/api/v1/me/active-community", residentToken, Map.of("tenantId", tenantB.toString()))
                .get("token").asText();
        assertEquals(200, http(HttpMethod.GET, "/api/v1/tickets/" + id, bAgain, null).getStatusCode().value());
    }

    @Test
    void switchingToANonMemberCommunityIsForbidden() {
        UUID tenantC = createTenant(su, "Palm Grove", "#7B1FA2");
        var r = http(HttpMethod.POST, "/api/v1/me/active-community", residentToken,
                Map.of("tenantId", tenantC.toString()));
        assertEquals(403, r.getStatusCode().value());
    }

    @Test
    void leavingFallsBackThenDropsToOnboarding() {
        post("/api/v1/me/active-community", residentToken, Map.of("tenantId", tenantB.toString()));

        JsonNode afterLeaveA = post("/api/v1/me/memberships/" + tenantA + "/leave", residentToken, Map.of());
        assertEquals(tenantB.toString(), afterLeaveA.get("user").get("activeTenantId").asText());
        boolean aExited = false;
        for (JsonNode m : get("/api/v1/me", residentToken).get("memberships")) {
            if (m.get("tenantId").asText().equals(tenantA.toString())) aExited = m.get("status").asText().equals("EXITED");
        }
        assertTrue(aExited, "community A membership is EXITED");

        JsonNode afterLeaveB = post("/api/v1/me/memberships/" + tenantB + "/leave", residentToken, Map.of());
        assertFalse(afterLeaveB.get("user").hasNonNull("activeTenantId"));
        // MVP-8: a resident with no community is READY (a community-less individual), not NEEDS_COMMUNITY.
        assertEquals("READY", afterLeaveB.get("onboardingState").asText());
    }
}
