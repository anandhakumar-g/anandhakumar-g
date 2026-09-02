package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVP-5 (A1): when a community turns on the approval-of-allocation gate, an assigned ticket
 * parks in PENDING_RESIDENT_APPROVAL and the provider is engaged only after the resident approves.
 */
class AllocationApprovalIT extends IntegrationTestBase {

    private Marketplace mk;
    private String residentToken;
    private String providerUserId;
    private String catId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
        catId = firstCategoryId(residentToken, "Electrical");
        providerUserId = get("/api/v1/me", mk.providerToken()).get("userId").asText();
        // register a device per actor so ticket notifications are delivered (SENT), not SKIPPED
        for (String tok : new String[]{residentToken, mk.adminToken(), mk.providerToken()}) {
            post("/api/v1/me/devices", tok, Map.of("token", "ExponentPushToken[" + tok.hashCode() + "]",
                    "platform", "android"));
        }
    }

    private String raise() {
        return post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", catId, "description", "Kitchen plug sparking",
                "serviceAddressText", "A-101")).get("id").asText();
    }

    @Test
    void gateOnParksTheTicketAndDefersTheProviderNotification() {
        setAllocationApproval(mk.superToken(), mk.tenantId(), true);
        String id = raise();

        JsonNode assigned = post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));
        assertEquals("PENDING_RESIDENT_APPROVAL", assigned.get("status").asText());
        assertTrue(assigned.get("assignedProvider").hasNonNull("name"));
        assertFalse(assigned.get("allocationApprovedByResident").asBoolean());

        awaitOutboxDrained();
        assertEquals(0, notificationCount(providerUserId, "TICKET", "SENT"),
                "provider must not be notified while the ticket is parked");
        assertTrue(notificationCount(userId(residentToken), "TICKET_PENDING_RESIDENT_APPROVAL", "SENT") >= 1);
    }

    @Test
    void residentApprovesThenTheProviderIsEngaged() {
        setAllocationApproval(mk.superToken(), mk.tenantId(), true);
        String id = raise();
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));

        JsonNode approved = post("/api/v1/tickets/" + id + "/allocation/approve", residentToken, Map.of());
        assertEquals("ASSIGNED", approved.get("status").asText());
        assertTrue(approved.get("allocationApprovedByResident").asBoolean());
        assertTrue(approved.hasNonNull("assignedAt"));

        awaitOutboxDrained();
        assertTrue(notificationCount(providerUserId, "TICKET_ASSIGNED", "SENT") >= 1);

        // the provider can now act
        assertEquals("ACCEPTED", post("/api/v1/tickets/" + id + "/accept", mk.providerToken(), Map.of())
                .get("status").asText());
    }

    @Test
    void declineRequiresAReason() {
        setAllocationApproval(mk.superToken(), mk.tenantId(), true);
        String id = raise();
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));

        var r = http(HttpMethod.POST, "/api/v1/tickets/" + id + "/allocation/reject", residentToken, Map.of());
        assertEquals(400, r.getStatusCode().value());
        assertEquals("SP-400-VALIDATION", r.getBody().get("errorCode").asText());
    }

    @Test
    void residentDeclineReturnsTheTicketToTheAdminQueue() {
        setAllocationApproval(mk.superToken(), mk.tenantId(), true);
        String id = raise();
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));

        JsonNode declined = post("/api/v1/tickets/" + id + "/allocation/reject", residentToken,
                Map.of("reason", "I won't be home this week"));
        assertEquals("ACKNOWLEDGED", declined.get("status").asText());
        assertFalse(declined.hasNonNull("assignedProvider"));

        awaitOutboxDrained();
        assertTrue(notificationCount(userId(mk.adminToken()), "TICKET_ACKNOWLEDGED", "SENT") >= 1);
    }

    @Test
    void providerCannotActWhileTheTicketIsParked() {
        setAllocationApproval(mk.superToken(), mk.tenantId(), true);
        String id = raise();
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));

        var r = http(HttpMethod.POST, "/api/v1/tickets/" + id + "/accept", mk.providerToken(), Map.of());
        assertEquals(409, r.getStatusCode().value());
        assertEquals("SP-409-TRANSITION", r.getBody().get("errorCode").asText());
    }

    @Test
    void gateOffKeepsTheDirectAssignBehaviour() {
        String id = raise();
        JsonNode assigned = post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));
        assertEquals("ASSIGNED", assigned.get("status").asText());
        awaitOutboxDrained();
        assertTrue(notificationCount(providerUserId, "TICKET_ASSIGNED", "SENT") >= 1);
    }

    @Test
    void rerouteWhileParkedRequiresAReasonAndReParks() {
        setAllocationApproval(mk.superToken(), mk.tenantId(), true);
        String id = raise();
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));

        var noReason = http(HttpMethod.POST, "/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));
        assertEquals(400, noReason.getStatusCode().value());

        JsonNode reparked = post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString(), "remarks", "resident asked for a different day"));
        assertEquals("PENDING_RESIDENT_APPROVAL", reparked.get("status").asText());
    }

    private String userId(String token) {
        return get("/api/v1/me", token).get("userId").asText();
    }
}
