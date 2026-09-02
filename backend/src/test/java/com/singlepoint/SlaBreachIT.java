package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.security.TenantContext;
import com.singlepoint.support.IntegrationTestBase;
import com.singlepoint.ticket.SlaBreachJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-5 (A2): an open ticket past its SLA due time is flagged once and its admins + provider alerted. */
class SlaBreachIT extends IntegrationTestBase {

    @Autowired SlaBreachJob slaBreachJob;

    private Marketplace mk;
    private String residentToken;
    private String adminUserId;
    private String providerUserId;
    private String catId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
        catId = firstCategoryId(residentToken, "Electrical"); // seeded with sla_hours = 24
        adminUserId = get("/api/v1/me", mk.adminToken()).get("userId").asText();
        providerUserId = get("/api/v1/me", mk.providerToken()).get("userId").asText();
        for (String tok : new String[]{mk.adminToken(), mk.providerToken()}) {
            post("/api/v1/me/devices", tok, Map.of("token", "ExponentPushToken[" + tok.hashCode() + "]",
                    "platform", "android"));
        }
    }

    private String raiseAndAssign() {
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", catId, "description", "sparking", "serviceAddressText", "A-1")).get("id").asText();
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));
        return id;
    }

    private void backdateSla(String ticketId) {
        TenantContext.setWildcard();
        try {
            jdbcTemplate.update("update ticket set sla_due_at = now() - interval '1 hour' where id = ?::uuid", ticketId);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void flagsABreachedTicketOnceAndAlertsAdminsAndProvider() {
        String id = raiseAndAssign();
        backdateSla(id);

        assertEquals(1, slaBreachJob.runNow());

        JsonNode view = get("/api/v1/tickets/" + id, mk.adminToken());
        assertTrue(view.hasNonNull("slaBreachedAt"));
        assertEquals("ASSIGNED", view.get("status").asText(), "the ticket is flagged, not moved");

        awaitOutboxDrained();
        assertTrue(notificationCount(adminUserId, "TICKET_SLA_BREACHED", "SENT") >= 1);
        assertTrue(notificationCount(providerUserId, "TICKET_SLA_BREACHED", "SENT") >= 1);

        // a second sweep is a no-op
        assertEquals(0, slaBreachJob.runNow());
    }

    @Test
    void leavesOnTimeAndResolvedTicketsAlone() {
        String onTime = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", catId, "description", "later", "serviceAddressText", "A-2")).get("id").asText();
        String breachedButResolved = raiseAndAssign();
        backdateSla(breachedButResolved);
        post("/api/v1/tickets/" + breachedButResolved + "/accept", mk.providerToken(), Map.of());
        post("/api/v1/tickets/" + breachedButResolved + "/status", mk.providerToken(), Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + breachedButResolved + "/status", mk.providerToken(),
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "done"));

        assertEquals(0, slaBreachJob.runNow());
        assertFalse(get("/api/v1/tickets/" + onTime, mk.adminToken()).hasNonNull("slaBreachedAt"));
    }
}
