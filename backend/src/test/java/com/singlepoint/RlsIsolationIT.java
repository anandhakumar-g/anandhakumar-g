package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Proves multi-tenant isolation is enforced by PostgreSQL RLS, not just app filtering. */
class RlsIsolationIT extends IntegrationTestBase {

    @Test
    void adminOfOneTenantCannotSeeAnothersTicket() {
        String su = login(SUPER_ADMIN_PHONE).token();

        UUID green = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, green, "+919000000101", "GM Admin");
        String greenAdmin = login("+919000000101").token();
        String greenCode = createInvite(greenAdmin);

        UUID lake = createTenant(su, "Lakeview", "#1565C0");
        createAdmin(su, lake, "+919000000201", "LV Admin");
        String lakeAdmin = login("+919000000201").token();

        // resident in Green raises a ticket
        String resToken = toSession(post("/api/v1/memberships/join",
                completeProfile(login("+919888000020").token(), "Ravi", "r@example.com").token(),
                Map.of("tenantId", green.toString(), "inviteCode", greenCode))).token();
        String cat = firstCategoryId(resToken, "Plumbing");
        String ticketId = post("/api/v1/tickets", resToken, Map.of(
                "categoryId", cat, "description", "leak", "serviceAddressText", "A-1")).get("id").asText();

        // Green admin sees it
        JsonNode greenQueue = get("/api/v1/tickets", greenAdmin);
        assertTrue(contains(greenQueue.get("content"), ticketId));

        // Lake admin does NOT — list excludes it, and direct GET is 404
        JsonNode lakeQueue = get("/api/v1/tickets", lakeAdmin);
        assertFalse(contains(lakeQueue.get("content"), ticketId));

        var direct = http(HttpMethod.GET, "/api/v1/tickets/" + ticketId, lakeAdmin, null);
        assertEquals(404, direct.getStatusCode().value());
    }

    private boolean contains(JsonNode arr, String id) {
        for (JsonNode n : arr) if (n.get("id").asText().equals(id)) return true;
        return false;
    }
}
