package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (A3): a mid-period paid → paid switch credits the unused days of the old plan. */
class ProrationIT extends IntegrationTestBase {

    @Test
    void switchingPlansMidPeriodProratesTheNewInvoice() {
        Marketplace mk = marketplace("+919000000141", "+919000000341");
        String admin = mk.adminToken();

        post("/api/v1/me/billing/plan", admin, Map.of("planCode", "TENANT_STANDARD")); // 2999
        JsonNode after = post("/api/v1/me/billing/plan", admin, Map.of("planCode", "TENANT_PLUS")); // 5999
        assertEquals("TENANT_PLUS", after.get("planCode").asText());

        JsonNode billing = get("/api/v1/me/billing", admin);
        assertEquals(1, billing.get("dueInvoices").size(), "exactly one DUE invoice — no stacked charges");
        BigDecimal amount = new BigDecimal(billing.get("dueInvoices").get(0).get("amount").asText());
        assertTrue(amount.compareTo(new BigDecimal("5999")) < 0, "new invoice is discounted");
        assertTrue(amount.compareTo(BigDecimal.ZERO) > 0, "but not free — 5999 > the ~2999 credit");
    }

    @Test
    void firstUpgradeHasNothingToProrate() {
        Marketplace mk = marketplace("+919000000142", "+919000000342");
        post("/api/v1/me/billing/plan", mk.adminToken(), Map.of("planCode", "TENANT_STANDARD"));
        JsonNode billing = get("/api/v1/me/billing", mk.adminToken());
        assertEquals(0, new BigDecimal(billing.get("dueInvoices").get(0).get("amount").asText())
                .compareTo(new BigDecimal("2999")));
    }
}
