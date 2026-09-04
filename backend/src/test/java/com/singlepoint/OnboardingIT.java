package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OnboardingIT extends IntegrationTestBase {

    @Test
    void residentJoinsWithInviteCode() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenant, "+919000000101", "GM Admin");
        String adminToken = login("+919000000101").token();
        String code = createInvite(adminToken);

        Session s = login("+919888000001");
        assertEquals("NEEDS_PROFILE", s.onboardingState());

        // MVP-8: a profile-complete resident is READY even before joining a community.
        s = completeProfile(s.token(), "Ravi", "ravi@example.com");
        assertEquals("READY", s.onboardingState());
        assertNull(s.activeTenantId());

        JsonNode joined = post("/api/v1/memberships/join", s.token(),
                Map.of("tenantId", tenant.toString(), "inviteCode", code));
        Session ready = toSession(joined);
        assertEquals("READY", ready.onboardingState());
        assertEquals(tenant, ready.activeTenantId());

        // branding surfaces on /me
        JsonNode me = get("/api/v1/me", ready.token());
        assertEquals("#2E7D32", me.get("activeTenantBranding").get("brandPrimaryColor").asText());
    }

    @Test
    void residentWithoutCodeGoesThroughApprovalQueue() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Lakeview", "#1565C0");
        createAdmin(su, tenant, "+919000000201", "LV Admin");
        String adminToken = login("+919000000201").token();

        Session s = completeProfile(login("+919888000002").token(), "Meera", "meera@example.com");
        JsonNode res = post("/api/v1/memberships/join", s.token(),
                Map.of("tenantId", tenant.toString(), "requestedFlatLabel", "B-204"));
        assertEquals("PENDING_APPROVAL", toSession(res).onboardingState());

        JsonNode pending = get("/api/v1/admin/join-requests", adminToken);
        assertEquals(1, pending.size());
        String membershipId = pending.get(0).get("id").asText();

        post("/api/v1/admin/join-requests/" + membershipId + "/approve", adminToken, Map.of());

        // resident re-checks: now READY
        JsonNode me = get("/api/v1/me", s.token());
        assertEquals(tenant.toString(), me.get("activeTenantId").asText());
    }

    @Test
    void badInviteCodeIsRejected() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Green Meadows", "#2E7D32");
        Session s = completeProfile(login("+919888000003").token(), "Sam", "sam@example.com");

        var r = http(HttpMethod.POST, "/api/v1/memberships/join", s.token(),
                Map.of("tenantId", tenant.toString(), "inviteCode", "NOPENOPE"));
        assertEquals(400, r.getStatusCode().value());
        assertEquals("SP-400", r.getBody().get("errorCode").asText());
    }
}
