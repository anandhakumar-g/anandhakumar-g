package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (A2): a saved payment method turns a paid plan into a gateway-driven recurring charge. */
class RecurringBillingIT extends IntegrationTestBase {

    @Test
    void recurringPlanIsDrivenByTheGatewayWebhook() {
        Marketplace mk = marketplace("+919000000131", "+919000000331");
        String admin = mk.adminToken();

        post("/api/v1/me/payment-methods", admin, Map.of("gatewayToken", "tok_stub_1"));

        JsonNode up = post("/api/v1/me/billing/plan", admin, Map.of("planCode", "TENANT_STANDARD"));
        String subId = up.get("gatewaySubscriptionId").asText();
        assertTrue(subId.startsWith("stub_sub_"), "a gateway subscription id was stored");
        assertEquals("PAST_DUE", up.get("status").asText());
        assertTrue(up.get("pendingMandateUrl").asText().contains("/dev/pay/sub/"));

        assertTrue(get("/api/v1/me/billing", admin).get("recurring").asBoolean());

        // the mandate's first charge
        http(HttpMethod.POST, "/dev/pay/sub/" + subId + "/settle", null, null);

        JsonNode b2 = get("/api/v1/me/billing", admin);
        assertEquals("ACTIVE", b2.get("subscription").get("status").asText());
        assertTrue(b2.get("dueInvoices").isEmpty());

        // a failed renewal charge halts it
        http(HttpMethod.POST, "/dev/pay/sub/" + subId + "/halted", null, null);
        assertEquals("PAST_DUE", get("/api/v1/me/billing", admin).get("subscription").get("status").asText());
    }

    @Test
    void withoutAPaymentMethodItStaysOnTheManualPayLink() {
        Marketplace mk = marketplace("+919000000132", "+919000000332");
        JsonNode up = post("/api/v1/me/billing/plan", mk.adminToken(), Map.of("planCode", "TENANT_STANDARD"));
        assertEquals("PAST_DUE", up.get("status").asText());
        assertNull(up.get("gatewaySubscriptionId")); // omitted by non_null inclusion
        assertFalse(get("/api/v1/me/billing", mk.adminToken()).get("recurring").asBoolean());
        assertEquals(1, get("/api/v1/me/billing", mk.adminToken()).get("dueInvoices").size());
    }
}
