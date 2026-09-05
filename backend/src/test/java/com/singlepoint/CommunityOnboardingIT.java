package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-11 (A): a resident self-onboards a community; the Super Admin approves or rejects it. */
class CommunityOnboardingIT extends IntegrationTestBase {

    private String superToken;
    private String resident;

    @BeforeEach
    void fixture() {
        superToken = login(SUPER_ADMIN_PHONE).token();
        resident = completeProfile(login("+919888700010").token(), "Nadia", "nadia@example.com").token();
    }

    @Test
    void submitAppearsOnlyInTheQueueThenApprovalMakesTheRequesterAnAdmin() {
        JsonNode req = post("/api/v1/onboarding/community", resident,
                Map.of("name", "Palm Grove", "city", "Bengaluru"));
        String tenantId = req.get("tenantId").asText();
        assertEquals("PENDING_REVIEW", req.get("status").asText());

        // invisible to residents and to the health list
        assertEquals(0, get("/api/v1/tenants?query=Palm", resident).get("content").size());
        assertEquals(0, countInHealth(tenantId));

        // in the review queue, requester hydrated + phone masked
        JsonNode queue = get("/api/v1/superadmin/community-requests", superToken).get("content");
        assertEquals(1, queue.size());
        assertEquals("Nadia", queue.get(0).get("requestedByName").asText());
        String masked = queue.get(0).get("requestedByPhoneMasked").asText();
        assertTrue(masked.endsWith("0010") && !masked.contains("98887"), "phone is masked, not raw: " + masked);

        post("/api/v1/superadmin/community-requests/" + tenantId + "/approve", superToken, Map.of());

        // the requester picks up ADMIN on their next refresh
        JsonNode session = post("/api/v1/auth/refresh", resident, Map.of());
        assertEquals("ADMIN", session.get("user").get("role").asText());
        String adminToken = session.get("token").asText();

        assertEquals(200, http(HttpMethod.GET, "/api/v1/admin/flats", adminToken, null).getStatusCode().value());
        assertTrue(get("/api/v1/admin/locations", adminToken).size() >= 1, "a Main location was seeded");

        awaitOutboxDrained();
        String uid = get("/api/v1/me", adminToken).get("userId").asText();
        assertTrue(notificationCount(uid, "COMMUNITY_APPROVED", "SENT") >= 1
                || notificationCount(uid, "COMMUNITY_APPROVED", "SKIPPED") >= 1);
    }

    @Test
    void aSecondRequestWhileOnePendingIsRejected() {
        post("/api/v1/onboarding/community", resident, Map.of("name", "First"));
        assertEquals(409, http(HttpMethod.POST, "/api/v1/onboarding/community", resident,
                Map.of("name", "Second")).getStatusCode().value());
    }

    @Test
    void rejectArchivesItAndLeavesTheRequesterAResident() {
        String tenantId = post("/api/v1/onboarding/community", resident, Map.of("name", "Nope Villas"))
                .get("tenantId").asText();
        post("/api/v1/superadmin/community-requests/" + tenantId + "/reject", superToken,
                Map.of("reason", "duplicate of an existing community"));

        assertEquals("ADMIN".equals(get("/api/v1/me", resident).get("role").asText()), false);
        assertEquals("RESIDENT", get("/api/v1/me", resident).get("role").asText());
        assertEquals("ARCHIVED", get("/api/v1/me/community-request", resident).get("status").asText());
    }

    @Test
    void theQueueIsSuperAdminOnly() {
        assertEquals(403, http(HttpMethod.GET, "/api/v1/superadmin/community-requests", resident, null)
                .getStatusCode().value());
    }

    private long countInHealth(String tenantId) {
        JsonNode health = get("/api/v1/superadmin/tenants", superToken);
        long n = 0;
        for (JsonNode t : health) if (t.get("id").asText().equals(tenantId)) n++;
        return n;
    }
}
