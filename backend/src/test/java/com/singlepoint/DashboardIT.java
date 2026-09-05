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

/** MVP-10 (A): one role-branching dashboard — ticket buckets + action items that need this caller. */
class DashboardIT extends IntegrationTestBase {

    @Autowired SlaBreachJob slaBreachJob;

    private Marketplace mk;
    private String resident;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000041", "Rita");
    }

    @Test
    void residentDashboardBucketsTicketsAndActionItems() {
        String cat = firstCategoryId(resident, "Electrical");
        String tid = post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "fix it", "serviceAddressText", "A-1")).get("id").asText();
        post("/api/v1/tickets/" + tid + "/assign", mk.adminToken(), Map.of("providerId", mk.providerId().toString()));
        post("/api/v1/tickets/" + tid + "/accept", mk.providerToken(), Map.of());
        post("/api/v1/tickets/" + tid + "/status", mk.providerToken(), Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + tid + "/status", mk.providerToken(),
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "done"));
        post("/api/v1/tickets/" + tid + "/payment/charge", mk.adminToken(), Map.of("amount", 250, "note", "parts"));

        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_ELECTRICAL);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(), Map.of("target", Map.of("targetType", "ALL_TENANTS")));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());

        JsonNode d = get("/api/v1/dashboard", resident);
        assertEquals("RESIDENT", d.get("role").asText());
        assertEquals(1, d.get("ticketsByStatus").get("RESOLVED").asInt());
        assertEquals(1, d.get("resolvedAwaitingCloseCount").asInt());
        assertEquals(1, d.get("pendingPaymentCount").asInt());
        assertTrue(d.get("activeOffersCount").asInt() >= 1);
    }

    @Test
    void providerDashboardCountsAwaitingAcceptAndRating() {
        String cat = firstCategoryId(resident, "Electrical");
        String tid = post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "fix it", "serviceAddressText", "A-1")).get("id").asText();
        post("/api/v1/tickets/" + tid + "/assign", mk.adminToken(), Map.of("providerId", mk.providerId().toString()));

        JsonNode d = get("/api/v1/dashboard", mk.providerToken());
        assertEquals("PROVIDER", d.get("role").asText());
        assertEquals(1, d.get("ticketsByStatus").get("ASSIGNED").asInt());
        assertEquals(1, d.get("awaitingAcceptCount").asInt());
        assertEquals(0, d.get("ratingCount").asInt());
    }

    @Test
    void adminDashboardCountsUnassignedSlaBreachAndJoinRequests() {
        // unassigned: raised, never assigned
        String cat = firstCategoryId(resident, "Electrical");
        post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "unassigned one", "serviceAddressText", "A-1"));

        // SLA-breached: assigned then backdated
        String breached = post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "sparking", "serviceAddressText", "A-2")).get("id").asText();
        post("/api/v1/tickets/" + breached + "/assign", mk.adminToken(), Map.of("providerId", mk.providerId().toString()));
        TenantContext.setWildcard();
        try {
            jdbcTemplate.update("update ticket set sla_due_at = now() - interval '1 hour' where id = ?::uuid", breached);
        } finally {
            TenantContext.clear();
        }
        assertEquals(1, slaBreachJob.runNow());

        // a pending join request (no invite code -> admin approval queue)
        String pendingResident = login("+919888000099").token();
        pendingResident = completeProfile(pendingResident, "Pending Rita", "pending@example.com").token();
        post("/api/v1/memberships/join", pendingResident, Map.of("tenantId", mk.tenantId().toString()));

        JsonNode d = get("/api/v1/dashboard", mk.adminToken());
        assertEquals("ADMIN", d.get("role").asText());
        assertEquals(1, d.get("unassignedCount").asInt());
        assertEquals(1, d.get("slaBreachedCount").asInt());
        assertEquals(1, d.get("pendingJoinRequestsCount").asInt());
    }

    @Test
    void superAdminDashboardCountsPlatformWideIncludingCommunityless() {
        String nomad = individualUser("+919888500001", "Nomad");
        bookGlobalProvider(nomad, mk.providerId(), "Electrical");

        JsonNode d = get("/api/v1/dashboard", mk.superToken());
        assertEquals("SUPER_ADMIN", d.get("role").asText());
        assertTrue(d.get("communityLessOpenTickets").asInt() >= 1);
        assertTrue(d.get("totalOpenTickets").asInt() >= d.get("communityLessOpenTickets").asInt());
        assertTrue(d.get("communityCount").asInt() >= 1);
        assertEquals(0, d.get("providersPendingVerificationCount").asInt());
    }
}
