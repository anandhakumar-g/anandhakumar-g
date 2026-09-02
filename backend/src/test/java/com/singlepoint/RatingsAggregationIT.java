package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-5 (A4): a provider's rating_avg / rating_count roll up from the tickets they resolved. */
class RatingsAggregationIT extends IntegrationTestBase {

    private Marketplace mk;
    private String residentToken;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
    }

    private void resolveAndClose(UUID providerId, String providerToken, Integer rating) {
        String id = resolvedTicket(residentToken, mk.adminToken(), providerToken, providerId);
        Map<String, Object> body = rating == null ? Map.of("remarks", "ok")
                : Map.of("rating", rating, "remarks", "thanks");
        post("/api/v1/tickets/" + id + "/close", residentToken, body);
    }

    private JsonNode providerRow(String sort, UUID id) {
        for (JsonNode p : get("/api/v1/admin/providers" + (sort == null ? "" : "?sort=" + sort), mk.adminToken())) {
            if (p.get("id").asText().equals(id.toString())) return p;
        }
        throw new IllegalStateException("provider not in directory: " + id);
    }

    @Test
    void averageAndCountRollUpFromRatedCloses() {
        resolveAndClose(mk.providerId(), mk.providerToken(), 5);
        resolveAndClose(mk.providerId(), mk.providerToken(), 3);

        JsonNode row = providerRow(null, mk.providerId());
        assertEquals(4.0, row.get("ratingAvg").asDouble(), 0.001);
        assertEquals(2, row.get("ratingCount").asInt());

        // an unrated close does not move the aggregate
        resolveAndClose(mk.providerId(), mk.providerToken(), null);
        row = providerRow(null, mk.providerId());
        assertEquals(4.0, row.get("ratingAvg").asDouble(), 0.001);
        assertEquals(2, row.get("ratingCount").asInt());
    }

    @Test
    void ratingSurfacesOnTheAssignedProviderInTheTicketView() {
        String id = resolvedTicket(residentToken, mk.adminToken(), mk.providerToken(), mk.providerId());
        post("/api/v1/tickets/" + id + "/close", residentToken, Map.of("rating", 4, "remarks", "good"));

        JsonNode view = get("/api/v1/tickets/" + id, mk.adminToken());
        assertEquals(4.0, view.get("assignedProvider").get("ratingAvg").asDouble(), 0.001);
        assertEquals(1, view.get("assignedProvider").get("ratingCount").asInt());
    }

    @Test
    void sortByRatingPutsTheHigherRatedProviderFirst() {
        UUID low = createVerifiedProvider(mk.adminToken(), mk.superToken(), "AAA Sparks", "+919000000401");
        String lowToken = login("+919000000401").token();

        resolveAndClose(mk.providerId(), mk.providerToken(), 5);  // "Sparky ..." → 5.0
        resolveAndClose(low, lowToken, 2);                        // "AAA Sparks" → 2.0

        JsonNode byName = get("/api/v1/admin/providers", mk.adminToken());
        assertEquals("AAA Sparks", byName.get(0).get("name").asText());

        JsonNode byRating = get("/api/v1/admin/providers?sort=rating", mk.adminToken());
        assertEquals(mk.providerId().toString(), byRating.get(0).get("id").asText());
    }
}
