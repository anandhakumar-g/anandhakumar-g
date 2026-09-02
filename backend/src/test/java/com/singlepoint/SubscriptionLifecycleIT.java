package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.billing.BillingService;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Assign → pay → renew → lapse (grace) → expire → comp, with the write gate at each stage. */
class SubscriptionLifecycleIT extends IntegrationTestBase {

    @Autowired BillingService billing;

    private String createPaidPlan(String su, String code) {
        return post("/api/v1/superadmin/plans", su, Map.of(
                "target", "TENANT", "code", code, "name", code, "price", 999,
                "entitlements", Map.of("TICKETS_PER_MONTH", -1, "ADMIN_SEATS", 5, "OFFERS_PER_MONTH", 10)))
                .get("code").asText();
    }

    private String subjectStatus(String su, UUID tenantId) {
        for (JsonNode s : get("/api/v1/superadmin/subscriptions?subjectType=TENANT", su)) {
            if (s.get("subjectId").asText().equals(tenantId.toString())) return s.get("status").asText();
        }
        return null;
    }

    private int raiseTicket(String resident) {
        return http(HttpMethod.POST, "/api/v1/tickets", resident, Map.of(
                "categoryId", firstCategoryId(resident, "Electrical"),
                "description", "x", "serviceAddressText", "A-1")).getStatusCode().value();
    }

    @Test
    void fullLifecycle() {
        var mk = marketplace("+919000000101", "+919000000301");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000081", "Nina");
        String su = mk.superToken();
        createPaidPlan(su, "TENANT_PAID");

        // assign paid, not comp => PAST_DUE + a DUE invoice with a pay link
        JsonNode sub = post("/api/v1/superadmin/subscriptions", su, Map.of(
                "subjectType", "TENANT", "subjectId", mk.tenantId().toString(), "planCode", "TENANT_PAID"));
        assertEquals("PAST_DUE", sub.get("status").asText());

        JsonNode dueInvoices = get("/api/v1/superadmin/invoices?status=DUE", su);
        assertEquals(1, dueInvoices.size());
        String link = dueInvoices.get(0).get("paymentLink").asText();
        assertTrue(link.contains("/dev/pay/"));

        // within grace, writes still work
        assertEquals(201, raiseTicket(resident));

        // settle the invoice through the dev checkout -> webhook fall-through activates the subscription
        String ref = link.substring(link.lastIndexOf('/') + 1);
        assertEquals(200, rest.postForEntity("/dev/pay/" + ref + "/settle", null, Void.class)
                .getStatusCode().value());

        assertEquals("ACTIVE", subjectStatus(su, mk.tenantId()));
        assertEquals(0, get("/api/v1/superadmin/invoices?status=DUE", su).size());
        assertEquals("PAID", get("/api/v1/superadmin/invoices?status=PAID", su).get(0).get("status").asText());

        // roll the clock past the period end and renew -> PAST_DUE again with a fresh invoice
        jdbcTemplate.update("update subscription set current_period_end = now() - interval '1 day' "
                + "where subject_id = ?::uuid", mk.tenantId().toString());
        billing.runRenewal();
        assertEquals("PAST_DUE", subjectStatus(su, mk.tenantId()));
        assertEquals(1, get("/api/v1/superadmin/invoices?status=DUE", su).size());
        assertEquals(201, raiseTicket(resident)); // still within the new grace window

        // roll past the grace window and renew -> EXPIRED, writes blocked, reads still fine
        jdbcTemplate.update("update subscription set grace_until = now() - interval '1 hour' "
                + "where subject_id = ?::uuid", mk.tenantId().toString());
        billing.runRenewal();
        assertEquals("EXPIRED", subjectStatus(su, mk.tenantId()));

        var blocked = http(HttpMethod.POST, "/api/v1/tickets", resident, Map.of(
                "categoryId", firstCategoryId(resident, "Electrical"),
                "description", "x", "serviceAddressText", "A-1"));
        assertEquals(402, blocked.getStatusCode().value());
        assertEquals("SP-402-SUBSCRIPTION", blocked.getBody().get("errorCode").asText());

        // reads keep working
        assertTrue(get("/api/v1/tickets", resident).size() >= 1);
    }

    @Test
    void compClearsDuesAndNeverInvoices() {
        var mk = marketplace("+919000000111", "+919000000311");
        String su = mk.superToken();
        createPaidPlan(su, "TENANT_PAID2");

        JsonNode sub = post("/api/v1/superadmin/subscriptions", su, Map.of(
                "subjectType", "TENANT", "subjectId", mk.tenantId().toString(), "planCode", "TENANT_PAID2"));
        assertEquals(1, get("/api/v1/superadmin/invoices?status=DUE", su).size());

        JsonNode comped = post("/api/v1/superadmin/subscriptions/" + sub.get("id").asText() + "/comp", su, Map.of());
        assertEquals("COMPED", comped.get("status").asText());
        assertEquals(0, get("/api/v1/superadmin/invoices?status=DUE", su).size());
    }
}
