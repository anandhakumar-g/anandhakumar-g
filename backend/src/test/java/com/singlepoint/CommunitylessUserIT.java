package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-8 (A): an individual with no community books a verified provider directly, tenant-free. */
class CommunitylessUserIT extends IntegrationTestBase {

    private String su;
    private UUID providerId;
    private String providerToken;
    private String nomad;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        providerId = createVerifiedProvider(su, "Sparky Electricals", "+919000000301");
        providerToken = login("+919000000301").token();
        nomad = individualUser("+919888500001", "Nomad");
    }

    @Test
    void meReportsReadyWithNoCommunity() {
        JsonNode me = get("/api/v1/me", nomad);
        assertFalse(me.hasNonNull("activeTenantId"));
        assertNull(me.get("activeTenantBranding") == null ? null : me.get("activeTenantBranding").textValue());
        assertEquals(0, me.get("memberships").size());
    }

    @Test
    void globalDirectoryIsVisibleAndContactFree() {
        JsonNode list = get("/api/v1/providers", nomad);
        boolean found = false;
        for (JsonNode p : list) {
            if (p.get("id").asText().equals(providerId.toString())) {
                found = true;
                assertFalse(p.has("contactPhone") || p.has("contactPhoneMasked"), "no contact details");
                assertTrue(p.get("assignable").asBoolean());
            }
        }
        assertTrue(found, "the verified global provider is bookable");
    }

    @Test
    void bookThenRunTheJobToClose() {
        String id = bookGlobalProvider(nomad, providerId, "Electrical");
        JsonNode t = get("/api/v1/tickets/" + id, nomad);
        assertEquals("ASSIGNED", t.get("status").asText());
        assertEquals("DIRECT_SERVICE", t.get("requestMode").asText());

        awaitOutboxDrained();
        String providerUserId = get("/api/v1/me", providerToken).get("userId").asText();
        assertTrue(notificationCount(providerUserId, "TICKET", "SENT") >= 1
                || notificationCount(providerUserId, "TICKET", "SKIPPED") >= 1,
                "the provider was notified of the assignment");

        assertEquals("ACCEPTED", post("/api/v1/tickets/" + id + "/accept", providerToken, Map.of()).get("status").asText());
        post("/api/v1/tickets/" + id + "/status", providerToken, Map.of("toStatus", "IN_PROGRESS"));
        assertEquals("RESOLVED", post("/api/v1/tickets/" + id + "/status", providerToken,
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "done")).get("status").asText());

        JsonNode closed = post("/api/v1/tickets/" + id + "/close", nomad, Map.of("rating", 5));
        assertEquals("CLOSED", closed.get("status").asText());
        assertEquals(5, closed.get("rating").asInt());

        // it shows only in the raiser's list and the provider's — never a community admin's
        boolean inMine = false;
        for (JsonNode row : get("/api/v1/tickets", nomad).get("content")) {
            if (row.get("id").asText().equals(id)) inMine = true;
        }
        assertTrue(inMine);
    }

    @Test
    void aCommunityAdminNeverSeesATenantlessTicket() {
        String id = bookGlobalProvider(nomad, providerId, "Electrical");
        var mk = marketplace("+919000000111", "+919000000311");
        assertEquals(404, http(HttpMethod.GET, "/api/v1/tickets/" + id, mk.adminToken(), null)
                .getStatusCode().value());
    }

    @Test
    void raisingWithoutAProviderIsRejected() {
        String cat = firstCategoryId(nomad, "Electrical");
        var r = http(HttpMethod.POST, "/api/v1/tickets", nomad, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "y"));
        assertEquals(400, r.getStatusCode().value());
    }

    @Test
    void cannotBookAnUnverifiedProvider() {
        UUID pending = UUID.fromString(post("/api/v1/superadmin/providers", su, Map.of(
                "name", "Pending Co", "vendorCategoryId", VENDOR_CAT_ELECTRICAL,
                "company", false, "contactPhone", "+919000000399")).get("id").asText());
        String cat = firstCategoryId(nomad, "Electrical");
        var r = http(HttpMethod.POST, "/api/v1/tickets", nomad, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "y",
                "providerId", pending.toString()));
        assertEquals(422, r.getStatusCode().value());
    }
}
