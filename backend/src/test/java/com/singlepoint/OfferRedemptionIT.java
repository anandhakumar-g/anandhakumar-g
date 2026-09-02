package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OfferRedemptionIT extends IntegrationTestBase {

    @Test
    void perUserAndTotalLimitsAreEnforced() {
        var mk = marketplace("+919000000101", "+919000000301");
        String a = joinResident(mk.tenantId(), mk.adminToken(), "+919888000051", "Asha");
        String b = joinResident(mk.tenantId(), mk.adminToken(), "+919888000052", "Bala");

        // total limit = 1, per-user = 1
        JsonNode o = post("/api/v1/offers", mk.providerToken(), Map.ofEntries(
                Map.entry("vendorCategoryId", VENDOR_CAT_RESTAURANT),
                Map.entry("title", "First 1 only"),
                Map.entry("discountType", "FLAT"),
                Map.entry("discountValue", 100),
                Map.entry("couponCode", "FIRST100"),
                Map.entry("validFrom", java.time.Instant.now().minusSeconds(60).toString()),
                Map.entry("validTo", java.time.Instant.now().plusSeconds(86400).toString()),
                Map.entry("redemptionLimitPerUser", 1),
                Map.entry("redemptionLimitTotal", 1)));
        String offerId = o.get("id").asText();
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "ALL_TENANTS")));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());

        JsonNode first = post("/api/v1/offers/" + offerId + "/redeem", a, Map.of());
        assertEquals("REDEEMED", first.get("status").asText());
        assertEquals("FIRST100", first.get("couponCode").asText());

        // same user again -> per-user limit
        var again = http(HttpMethod.POST, "/api/v1/offers/" + offerId + "/redeem", a, Map.of());
        assertEquals(409, again.getStatusCode().value());
        assertEquals("SP-409-OFFER", again.getBody().get("errorCode").asText());

        // different user -> total limit reached
        var bTry = http(HttpMethod.POST, "/api/v1/offers/" + offerId + "/redeem", b, Map.of());
        assertEquals(409, bTry.getStatusCode().value());
        assertEquals("SP-409-OFFER", bTry.getBody().get("errorCode").asText());
    }

    @Test
    void residentOnlySeesOffersTargetedToThem() {
        var mk = marketplace("+919000000111", "+919000000311");
        var other = marketplace("+919000000121", "+919000000321");
        String meThere = joinResident(other.tenantId(), other.adminToken(), "+919888000061", "Zoe");

        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "SINGLE_TENANT",
                        "tenantIds", java.util.List.of(mk.tenantId().toString()))));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());

        String residentHere = joinResident(mk.tenantId(), mk.adminToken(), "+919888000062", "Yan");
        assertEquals(1, get("/api/v1/offers", residentHere).size());
        assertEquals(0, get("/api/v1/offers", meThere).size());
    }
}
