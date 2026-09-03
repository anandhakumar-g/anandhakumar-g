package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketLifecycleIT extends IntegrationTestBase {

    private String residentToken;
    private String adminToken;
    private String providerToken;
    private String electricalCategoryId;

    @BeforeEach
    void fixture() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenant, "+919000000101", "GM Admin");
        adminToken = login("+919000000101").token();
        UUID sparky = createVerifiedProvider(su, "Sparky Electricals", "+919000000301");
        enableProviderOnboarding(su, tenant);
        enrolProvider(adminToken, sparky);
        providerToken = login("+919000000301").token();

        String code = createInvite(adminToken);
        Session r = completeProfile(login("+919888000010").token(), "Ravi", "ravi@example.com");
        residentToken = toSession(post("/api/v1/memberships/join", r.token(),
                Map.of("tenantId", tenant.toString(), "inviteCode", code))).token();
        electricalCategoryId = firstCategoryId(residentToken, "Electrical");
    }

    @Test
    void fullHappyPath() {
        JsonNode t = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", electricalCategoryId,
                "description", "Kitchen plug sparking",
                "serviceAddressText", "A-101, Green Meadows",
                "priority", "high"));
        String id = t.get("id").asText();
        assertEquals("NEW", t.get("status").asText());
        assertTrue(t.get("referenceCode").asText().startsWith("SP-"));

        // provider directory (assignable)
        JsonNode providers = get("/api/v1/admin/providers", adminToken);
        String providerId = providers.get(0).get("id").asText();
        assertTrue(providers.get(0).get("assignable").asBoolean());

        assertEquals("ASSIGNED", post("/api/v1/tickets/" + id + "/assign", adminToken,
                Map.of("providerId", providerId)).get("status").asText());
        assertEquals("ACCEPTED", post("/api/v1/tickets/" + id + "/accept", providerToken, Map.of()).get("status").asText());
        assertEquals("IN_PROGRESS", post("/api/v1/tickets/" + id + "/status", providerToken,
                Map.of("toStatus", "IN_PROGRESS")).get("status").asText());

        JsonNode resolved = post("/api/v1/tickets/" + id + "/status", providerToken,
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "Replaced socket"));
        assertEquals("RESOLVED", resolved.get("status").asText());
        // provider sees the full resident phone while engaged
        assertEquals("+919888000010", resolved.get("raisedBy").get("phone").asText());

        JsonNode closed = post("/api/v1/tickets/" + id + "/close", residentToken, Map.of("rating", 5, "remarks", "Fast"));
        assertEquals("CLOSED", closed.get("status").asText());
        assertEquals(5, closed.get("rating").asInt());

        // append-only timeline chain
        JsonNode timeline = get("/api/v1/tickets/" + id + "/timeline", adminToken);
        List<String> statuses = new java.util.ArrayList<>();
        timeline.forEach(e -> statuses.add(e.get("toStatus").asText()));
        assertEquals(List.of("NEW", "ASSIGNED", "ACCEPTED", "IN_PROGRESS", "RESOLVED", "CLOSED"), statuses);

        // admin sees resident phone masked
        JsonNode adminView = get("/api/v1/tickets/" + id, adminToken);
        assertNotEquals("+919888000010", adminView.get("raisedBy").get("phone").asText());
        assertTrue(adminView.get("raisedBy").get("phone").asText().endsWith("0010"));
    }

    @Test
    void illegalTransitionRejected() {
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", electricalCategoryId, "description", "test",
                "serviceAddressText", "A-101")).get("id").asText();
        String providerId = get("/api/v1/admin/providers", adminToken).get(0).get("id").asText();
        post("/api/v1/tickets/" + id + "/assign", adminToken, Map.of("providerId", providerId));

        // Ticket is ASSIGNED; provider jumping straight to RESOLVED is not a legal transition.
        var r = http(HttpMethod.POST, "/api/v1/tickets/" + id + "/status", providerToken,
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "nope"));
        assertEquals(409, r.getStatusCode().value());
        assertEquals("SP-409-TRANSITION", r.getBody().get("errorCode").asText());
    }

    @Test
    void cannotAssignUnverifiedProvider() {
        String su = login(SUPER_ADMIN_PHONE).token();
        JsonNode dodgy = post("/api/v1/superadmin/providers", su, Map.of(
                "name", "Dodgy Co", "vendorCategoryId", VENDOR_CAT_ELECTRICAL, "company", false,
                "contactPhone", "+919777700009"));
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", electricalCategoryId, "description", "x", "serviceAddressText", "A-101")).get("id").asText();

        var r = http(HttpMethod.POST, "/api/v1/tickets/" + id + "/assign", adminToken,
                Map.of("providerId", dodgy.get("id").asText()));
        assertEquals(422, r.getStatusCode().value());
        assertEquals("SP-422-PROVIDER", r.getBody().get("errorCode").asText());
    }

    @Test
    void statusHistoryIsAppendOnly() {
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", electricalCategoryId, "description", "x", "serviceAddressText", "A-101")).get("id").asText();
        // wildcard so RLS makes the row visible to the raw JDBC update; the append-only
        // trigger must then reject the mutation.
        com.singlepoint.security.TenantContext.setWildcard();
        try {
            assertTrue(jdbcTemplate.queryForObject(
                    "select count(*) from ticket_status_history where ticket_id = ?::uuid", Integer.class, id) > 0);
            assertThrows(DataAccessException.class,
                    () -> jdbcTemplate.update("update ticket_status_history set remarks = 'tampered' where ticket_id = ?::uuid", id));
            assertThrows(DataAccessException.class,
                    () -> jdbcTemplate.update("delete from ticket_status_history where ticket_id = ?::uuid", id));
        } finally {
            com.singlepoint.security.TenantContext.clear();
        }
    }
}
