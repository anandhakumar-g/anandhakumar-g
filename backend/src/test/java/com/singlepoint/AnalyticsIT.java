package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-11 (B): platform analytics — time-bucketed series + "now" totals, Super Admin only. */
class AnalyticsIT extends IntegrationTestBase {

    private Marketplace mk;
    private String resident;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000041", "Rita");
        // one ticket + one offer redemption, all "now"
        String cat = firstCategoryId(resident, "Electrical");
        post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "A-1"));
        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_ELECTRICAL);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "ALL_TENANTS")));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
        post("/api/v1/offers/" + offerId + "/redeem", resident, Map.of());
    }

    @Test
    void weeklySeriesCountsTheCurrentBucketAndReportsTotals() {
        JsonNode a = get("/api/v1/superadmin/analytics?bucket=WEEK&points=8", mk.superToken());
        assertEquals("WEEK", a.get("bucket").asText());
        JsonNode series = a.get("series");
        assertEquals(8, series.size());
        JsonNode latest = series.get(series.size() - 1);
        assertTrue(latest.get("ticketsCreated").asInt() >= 1);
        assertTrue(latest.get("offersRedeemed").asInt() >= 1);
        assertTrue(latest.get("newUsers").asInt() >= 1);

        JsonNode totals = a.get("totals");
        assertTrue(totals.get("communities").asInt() >= 1);
        assertTrue(totals.get("residents").asInt() >= 1);
        assertTrue(totals.get("providers").asInt() >= 1);
        assertTrue(totals.get("openTickets").asInt() >= 1);
    }

    @Test
    void monthBucketAlsoWorks() {
        JsonNode a = get("/api/v1/superadmin/analytics?bucket=MONTH&points=3", mk.superToken());
        assertEquals("MONTH", a.get("bucket").asText());
        assertEquals(3, a.get("series").size());
    }

    @Test
    void unknownBucketIs400_andNonSuperIs403() {
        assertEquals(400, http(HttpMethod.GET, "/api/v1/superadmin/analytics?bucket=DAILY", mk.superToken(), null)
                .getStatusCode().value());
        assertEquals(403, http(HttpMethod.GET, "/api/v1/superadmin/analytics", mk.adminToken(), null)
                .getStatusCode().value());
    }
}
