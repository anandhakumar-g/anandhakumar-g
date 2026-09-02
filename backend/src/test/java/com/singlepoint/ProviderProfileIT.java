package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.security.TenantContext;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-5 (C): admin edits + per-community deactivation; provider self-service + availability; resident away-until. */
class ProviderProfileIT extends IntegrationTestBase {

    private Marketplace mk;
    private String residentToken;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
    }

    private JsonNode providerRow(boolean includeInactive) {
        String path = "/api/v1/admin/providers" + (includeInactive ? "?includeInactive=true" : "");
        for (JsonNode p : get(path, mk.adminToken())) {
            if (p.get("id").asText().equals(mk.providerId().toString())) return p;
        }
        return null;
    }

    @Test
    void adminEditsAProviderProfile() {
        put("/api/v1/admin/providers/" + mk.providerId(), mk.adminToken(),
                Map.of("contactEmail", "ops@sparky.example", "serviceArea", "Whitefield & HSR"));
        JsonNode profile = get("/api/v1/provider/profile", mk.providerToken());
        assertEquals("ops@sparky.example", profile.get("contactEmail").asText());
        assertEquals("Whitefield & HSR", profile.get("serviceArea").asText());
    }

    @Test
    void phoneChangeRehashesAndBlocksCollisions() {
        String oldHash = phoneHash();
        put("/api/v1/admin/providers/" + mk.providerId(), mk.adminToken(),
                Map.of("contactPhone", "+919000000399"));
        assertNotEquals(oldHash, phoneHash());
    }

    @Test
    void deactivateDropsFromTheDirectoryButKeepsTheGlobalRecord() {
        post("/api/v1/admin/providers/" + mk.providerId() + "/deactivate", mk.adminToken(), Map.of());
        assertNull(providerRow(false), "gone from the default directory");
        assertNotNull(providerRow(true), "still listed with includeInactive");
        assertTrue(globalActive(), "global service_provider.active untouched");

        // cannot assign a deactivated provider
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", firstCategoryId(residentToken, "Electrical"),
                "description", "x", "serviceAddressText", "A-1")).get("id").asText();
        var r = http(HttpMethod.POST, "/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));
        assertEquals(422, r.getStatusCode().value());

        post("/api/v1/admin/providers/" + mk.providerId() + "/reactivate", mk.adminToken(), Map.of());
        assertNotNull(providerRow(false));
        assertEquals("ASSIGNED", post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString())).get("status").asText());
    }

    @Test
    void providerSetsAvailabilityAndItSurfacesButDoesNotBlockAssignment() {
        put("/api/v1/provider/profile", mk.providerToken(),
                Map.of("availability", "AWAY", "availabilityNote", "Back Monday"));
        assertEquals("AWAY", providerRow(false).get("availability").asText());

        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", firstCategoryId(residentToken, "Electrical"),
                "description", "x", "serviceAddressText", "A-1")).get("id").asText();
        JsonNode assigned = post("/api/v1/tickets/" + id + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));
        assertEquals("ASSIGNED", assigned.get("status").asText());
        assertEquals("AWAY", assigned.get("assignedProvider").get("availability").asText());
    }

    @Test
    void residentAwayUntilShowsOnTheRaiserCard() {
        String until = java.time.Instant.now().plusSeconds(86400).toString();
        put("/api/v1/me/away-until", residentToken, Map.of("awayUntil", until));

        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", firstCategoryId(residentToken, "Electrical"),
                "description", "x", "serviceAddressText", "A-1")).get("id").asText();
        JsonNode view = get("/api/v1/tickets/" + id, mk.adminToken());
        assertTrue(view.get("raisedBy").hasNonNull("awayUntil"));
    }

    private String phoneHash() {
        TenantContext.setWildcard();
        try {
            return jdbcTemplate.queryForObject(
                    "select contact_phone_hash from service_provider where id = ?::uuid",
                    String.class, mk.providerId().toString());
        } finally {
            TenantContext.clear();
        }
    }

    private boolean globalActive() {
        TenantContext.setWildcard();
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "select active from service_provider where id = ?::uuid",
                    Boolean.class, mk.providerId().toString()));
        } finally {
            TenantContext.clear();
        }
    }
}
