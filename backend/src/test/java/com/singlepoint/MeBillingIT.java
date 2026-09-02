package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The subject-facing billing view: plan, usage vs limits, self-upgrade and invoice payment. */
class MeBillingIT extends IntegrationTestBase {

    private long limitFor(JsonNode billing, String feature) {
        for (JsonNode u : billing.get("usage")) if (u.get("feature").asText().equals(feature)) return u.get("limit").asLong();
        throw new AssertionError("no usage row for " + feature);
    }

    private long usedFor(JsonNode billing, String feature) {
        for (JsonNode u : billing.get("usage")) if (u.get("feature").asText().equals(feature)) return u.get("used").asLong();
        throw new AssertionError("no usage row for " + feature);
    }

    @Test
    void adminSeesCommunityPlanAndUsage() {
        var mk = marketplace("+919000000101", "+919000000301");
        JsonNode b = get("/api/v1/me/billing", mk.adminToken());

        assertEquals("TENANT", b.get("subjectType").asText());
        assertEquals("TENANT_FREE", b.get("plan").get("code").asText());
        assertTrue(b.get("subscription") == null || b.get("subscription").isNull());
        assertEquals(5, limitFor(b, "ADMIN_SEATS"));
        assertEquals(1, usedFor(b, "ADMIN_SEATS"));           // the admin created by marketplace
        assertEquals(-1, limitFor(b, "TICKETS_PER_MONTH"));   // unlimited
        assertFalse(b.get("upgradeOptions").isEmpty());
    }

    @Test
    void providerSeesListingPlan() {
        var mk = marketplace("+919000000111", "+919000000311");
        JsonNode b = get("/api/v1/me/billing", mk.providerToken());

        assertEquals("PROVIDER", b.get("subjectType").asText());
        assertEquals("PROVIDER_FREE", b.get("plan").get("code").asText());
        assertEquals(1, limitFor(b, "DIRECTORY_LISTING"));
    }

    @Test
    void adminSelfUpgradesThenPaysTheInvoice() {
        var mk = marketplace("+919000000121", "+919000000321");

        JsonNode sub = post("/api/v1/me/billing/plan", mk.adminToken(), Map.of("planCode", "TENANT_STANDARD"));
        assertEquals("PAST_DUE", sub.get("status").asText());

        JsonNode b = get("/api/v1/me/billing", mk.adminToken());
        assertEquals("TENANT_STANDARD", b.get("plan").get("code").asText());
        assertEquals(1, b.get("dueInvoices").size());
        String invoiceId = b.get("dueInvoices").get(0).get("id").asText();

        JsonNode pay = post("/api/v1/me/billing/invoices/" + invoiceId + "/pay", mk.adminToken(), Map.of());
        String link = pay.get("paymentLink").asText();
        assertTrue(link.contains("/dev/pay/"));

        String ref = link.substring(link.lastIndexOf('/') + 1);
        assertEquals(200, rest.postForEntity("/dev/pay/" + ref + "/settle", null, Void.class)
                .getStatusCode().value());

        JsonNode after = get("/api/v1/me/billing", mk.adminToken());
        assertEquals("ACTIVE", after.get("subscription").get("status").asText());
        assertEquals(0, after.get("dueInvoices").size());
    }
}
