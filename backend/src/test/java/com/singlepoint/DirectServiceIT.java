package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-6 (B): a resident books a verified provider directly when the community opts in. */
class DirectServiceIT extends IntegrationTestBase {

    private Marketplace mk;
    private String residentToken;
    private String residentUserId;
    private String providerUserId;
    private String adminUserId;
    private String catId;
    private UUID provider2;
    private String provider2Token;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
        catId = firstCategoryId(residentToken, "Electrical");
        residentUserId = get("/api/v1/me", residentToken).get("userId").asText();
        providerUserId = get("/api/v1/me", mk.providerToken()).get("userId").asText();
        adminUserId = get("/api/v1/me", mk.adminToken()).get("userId").asText();
        provider2 = createVerifiedProvider(mk.adminToken(), mk.superToken(), "Bolt Electric", "+919000000401");
        provider2Token = login("+919000000401").token();
        for (String tok : new String[]{residentToken, mk.adminToken(), mk.providerToken(), provider2Token}) {
            post("/api/v1/me/devices", tok, Map.of("token", "ExponentPushToken[" + tok.hashCode() + "]",
                    "platform", "android"));
        }
    }

    private String rawRaise(Map<String, Object> body, String token) {
        return post("/api/v1/tickets", token, body).get("id").asText();
    }

    @Test
    void flagOffBlocksTheDirectoryAndDirectRaise() {
        assertEquals(403, http(HttpMethod.GET, "/api/v1/providers", residentToken, null).getStatusCode().value());
        var r = http(HttpMethod.POST, "/api/v1/tickets", residentToken, Map.of(
                "categoryId", catId, "description", "x", "serviceAddressText", "A-1",
                "providerId", mk.providerId().toString()));
        assertEquals(403, r.getStatusCode().value());
    }

    @Test
    void directoryIsContactFreeAndRatingShaped() {
        enableDirectService(mk.superToken(), mk.tenantId());
        JsonNode list = get("/api/v1/providers", residentToken);
        assertTrue(list.size() >= 2);
        JsonNode p = list.get(0);
        assertFalse(p.has("phone") || p.has("contactPhone") || p.has("contactEmail"), "no contact details");
        assertTrue(p.has("tier") && p.has("availability") && p.has("vendorCategoryLabel") && p.has("ratingCount"));
    }

    @Test
    void directBookingGoesStraightToAssignedAndNotifiesBoth() {
        enableDirectService(mk.superToken(), mk.tenantId());
        JsonNode t = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", catId, "description", "sparking plug", "serviceAddressText", "A-1",
                "providerId", mk.providerId().toString()));
        String id = t.get("id").asText();
        assertEquals("DIRECT_SERVICE", t.get("requestMode").asText());
        assertEquals("ASSIGNED", t.get("status").asText());
        assertTrue(t.get("allocationApprovedByResident").asBoolean());
        assertTrue(t.hasNonNull("assignedAt"));

        awaitOutboxDrained();
        assertTrue(notificationCount(providerUserId, "TICKET_ASSIGNED", "SENT") >= 1);
        assertTrue(notificationCount(adminUserId, "TICKET_ASSIGNED", "SENT") >= 1);

        // full lifecycle still works with no admin
        post("/api/v1/tickets/" + id + "/accept", mk.providerToken(), Map.of());
        post("/api/v1/tickets/" + id + "/status", mk.providerToken(), Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + id + "/status", mk.providerToken(),
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "fixed"));
        assertEquals("CLOSED", post("/api/v1/tickets/" + id + "/close", residentToken, Map.of("rating", 5))
                .get("status").asText());
    }

    @Test
    void residentRebooksAfterAReject() {
        enableDirectService(mk.superToken(), mk.tenantId());
        String id = rawRaise(Map.of("categoryId", catId, "description", "x", "serviceAddressText", "A-1",
                "providerId", mk.providerId().toString()), residentToken);
        post("/api/v1/tickets/" + id + "/reject", mk.providerToken(), Map.of("reason", "fully booked"));
        assertEquals("REJECTED", get("/api/v1/tickets/" + id, residentToken).get("status").asText());

        JsonNode rebooked = post("/api/v1/tickets/" + id + "/rebook", residentToken,
                Map.of("providerId", provider2.toString()));
        assertEquals("ASSIGNED", rebooked.get("status").asText());
        assertEquals("DIRECT_SERVICE", rebooked.get("requestMode").asText());
        assertEquals("Bolt Electric", rebooked.get("assignedProvider").get("name").asText());
    }

    @Test
    void unassignableProviderIs422AndRegressionHolds() {
        enableDirectService(mk.superToken(), mk.tenantId());
        post("/api/v1/admin/providers/" + mk.providerId() + "/deactivate", mk.adminToken(), Map.of());
        var r = http(HttpMethod.POST, "/api/v1/tickets", residentToken, Map.of(
                "categoryId", catId, "description", "x", "serviceAddressText", "A-1",
                "providerId", mk.providerId().toString()));
        assertEquals(422, r.getStatusCode().value());

        JsonNode plain = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", catId, "description", "x", "serviceAddressText", "A-1"));
        assertEquals("NEW", plain.get("status").asText());
        assertEquals("COMMUNITY_TICKET", plain.get("requestMode").asText());
    }

    @Test
    void communityAdminCanAlsoToggleDirectService() {
        put("/api/v1/admin/community-settings", mk.adminToken(), Map.of("directServiceEnabled", true));
        assertEquals(200, http(HttpMethod.GET, "/api/v1/providers", residentToken, null).getStatusCode().value());
    }
}
