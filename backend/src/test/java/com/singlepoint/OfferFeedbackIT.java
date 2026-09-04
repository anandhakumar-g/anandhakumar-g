package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-8 (B4): a resident rates an offer; the vendor sees the aggregate + the list. */
class OfferFeedbackIT extends IntegrationTestBase {

    private Marketplace mk;
    private String resident;
    private String offerId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000071", "Ravi");
        offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "ALL_TENANTS")));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
    }

    @Test
    void rateThenSeeItAggregatedAndUpsertOnASecondRating() {
        JsonNode f = post("/api/v1/offers/" + offerId + "/feedback", resident,
                Map.of("rating", 4, "comment", "solid deal"));
        assertEquals(4, f.get("rating").asInt());

        JsonNode mine = get("/api/v1/offers/mine", mk.providerToken());
        JsonNode row = null;
        for (JsonNode o : mine) if (o.get("id").asText().equals(offerId)) row = o;
        assertNotNull(row);
        assertEquals(4.0, row.get("ratingAvg").asDouble(), 0.001);
        assertEquals(1, row.get("ratingCount").asInt());

        // same resident re-rates -> still one row, new average
        post("/api/v1/offers/" + offerId + "/feedback", resident, Map.of("rating", 2));
        JsonNode list = get("/api/v1/offers/" + offerId + "/feedback", mk.providerToken());
        assertEquals(1, list.get("items").size());
        assertEquals(2.0, list.get("ratingAvg").asDouble(), 0.001);
    }

    @Test
    void onlyTheAuthorOrSuperAdminCanReadTheFeedbackList() {
        post("/api/v1/offers/" + offerId + "/feedback", resident, Map.of("rating", 5));

        // a different provider
        var other = marketplace("+919000000111", "+919000000311");
        assertEquals(404, http(HttpMethod.GET, "/api/v1/offers/" + offerId + "/feedback",
                other.providerToken(), null).getStatusCode().value());

        // the Super Admin can
        assertEquals(200, http(HttpMethod.GET, "/api/v1/offers/" + offerId + "/feedback",
                mk.superToken(), null).getStatusCode().value());
    }

    @Test
    void ratingOutOfRangeIsRejected() {
        var r = http(HttpMethod.POST, "/api/v1/offers/" + offerId + "/feedback", resident,
                Map.of("rating", 6));
        assertEquals(400, r.getStatusCode().value());
    }
}
