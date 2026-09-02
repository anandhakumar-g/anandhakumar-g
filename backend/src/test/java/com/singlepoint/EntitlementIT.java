package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Plan entitlements are enforced once a capped plan is assigned; FREE defaults never block. */
class EntitlementIT extends IntegrationTestBase {

    /** Create a $0 (immediately-ACTIVE) plan with the given caps and pin it to the subject. */
    private void assignCappedPlan(String su, String target, String code, UUID subjectId, Map<String, Object> caps) {
        post("/api/v1/superadmin/plans", su, Map.of(
                "target", target, "code", code, "name", code, "price", 0, "entitlements", caps));
        post("/api/v1/superadmin/subscriptions", su, Map.of(
                "subjectType", target, "subjectId", subjectId.toString(), "planCode", code));
    }

    private String raiseTicket(String resident) {
        String cat = firstCategoryId(resident, "Electrical");
        return http(HttpMethod.POST, "/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "A-1")).getStatusCode().value() + "";
    }

    @Test
    void ticketsPerMonthCapIsEnforced_andUnlimitedNeverBlocks() {
        var mk = marketplace("+919000000101", "+919000000301");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000081", "Nina");

        // FREE default => TICKETS_PER_MONTH:-1 => no cap
        for (int i = 0; i < 4; i++) assertEquals("201", raiseTicket(resident));

        assignCappedPlan(mk.superToken(), "TENANT", "TENANT_CAP2", mk.tenantId(),
                Map.of("TICKETS_PER_MONTH", 2));

        // usage already 4 >= 2 => the very next raise is blocked
        var blocked = http(HttpMethod.POST, "/api/v1/tickets", resident, Map.of(
                "categoryId", firstCategoryId(resident, "Electrical"),
                "description", "x", "serviceAddressText", "A-1"));
        assertEquals(402, blocked.getStatusCode().value());
        assertEquals("SP-402-QUOTA", blocked.getBody().get("errorCode").asText());
    }

    @Test
    void adminSeatsCapIsEnforcedOnCreateAdmin() {
        var mk = marketplace("+919000000111", "+919000000311");
        // one admin already exists (from marketplace)
        assignCappedPlan(mk.superToken(), "TENANT", "TENANT_SEAT1", mk.tenantId(),
                Map.of("ADMIN_SEATS", 1, "TICKETS_PER_MONTH", -1));

        var r = http(HttpMethod.POST, "/api/v1/superadmin/tenants/" + mk.tenantId() + "/admins",
                mk.superToken(), Map.of("phone", "+919000000112", "name", "Second Admin"));
        assertEquals(402, r.getStatusCode().value());
        assertEquals("SP-402-QUOTA", r.getBody().get("errorCode").asText());
    }

    @Test
    void offersPerMonthCapIsEnforcedForAProvider() {
        var mk = marketplace("+919000000121", "+919000000321");
        assignCappedPlan(mk.superToken(), "PROVIDER", "PROV_OFF1", mk.providerId(),
                Map.of("DIRECTORY_LISTING", 1, "OFFERS_PER_MONTH", 1));

        createDraftOffer(mk.providerToken(), VENDOR_CAT_ELECTRICAL); // 1st: fine

        var second = http(HttpMethod.POST, "/api/v1/offers", mk.providerToken(), Map.ofEntries(
                Map.entry("vendorCategoryId", VENDOR_CAT_ELECTRICAL),
                Map.entry("title", "Another"), Map.entry("description", "d"),
                Map.entry("discountType", "PERCENTAGE"), Map.entry("discountValue", 10),
                Map.entry("couponCode", "X2"),
                Map.entry("validFrom", java.time.Instant.now().minusSeconds(60).toString()),
                Map.entry("validTo", java.time.Instant.now().plus(java.time.Duration.ofDays(30)).toString()),
                Map.entry("redemptionLimitPerUser", 1)));
        assertEquals(402, second.getStatusCode().value());
        assertEquals("SP-402-QUOTA", second.getBody().get("errorCode").asText());
    }

    @Test
    void disabledFeature_zeroLimit_blocksImmediately() {
        var mk = marketplace("+919000000131", "+919000000331");
        assignCappedPlan(mk.superToken(), "TENANT", "TENANT_NOOFFERS", mk.tenantId(),
                Map.of("OFFERS_PER_MONTH", 0, "TICKETS_PER_MONTH", -1));

        var r = http(HttpMethod.POST, "/api/v1/offers", mk.adminToken(), Map.ofEntries(
                Map.entry("vendorCategoryId", VENDOR_CAT_ELECTRICAL),
                Map.entry("title", "Nope"), Map.entry("description", "d"),
                Map.entry("discountType", "PERCENTAGE"), Map.entry("discountValue", 10),
                Map.entry("couponCode", "NO1"),
                Map.entry("validFrom", java.time.Instant.now().minusSeconds(60).toString()),
                Map.entry("validTo", java.time.Instant.now().plus(java.time.Duration.ofDays(30)).toString()),
                Map.entry("redemptionLimitPerUser", 1)));
        assertEquals(402, r.getStatusCode().value());
        assertEquals("SP-402-QUOTA", r.getBody().get("errorCode").asText());
    }
}
