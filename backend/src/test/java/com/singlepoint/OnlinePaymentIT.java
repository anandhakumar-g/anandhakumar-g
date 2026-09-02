package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OnlinePaymentIT extends IntegrationTestBase {

    @Test
    void onlinePaymentViaWebhook() {
        var mk = marketplace("+919000000101", "+919000000301");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000081", "Nina");
        String ticketId = resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());

        post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.providerToken(), Map.of("amount", 750));
        JsonNode mode = post("/api/v1/tickets/" + ticketId + "/payment/mode", resident, Map.of("mode", "ONLINE"));
        String payLink = mode.get("payLink").asText();
        assertTrue(payLink.contains("/dev/pay/"));
        String ref = payLink.substring(payLink.lastIndexOf('/') + 1);

        // the dev checkout page settles by posting a correctly-signed synthetic webhook
        var settle = rest.postForEntity("/dev/pay/" + ref + "/settle", null, Void.class);
        assertEquals(200, settle.getStatusCode().value());

        JsonNode after = get("/api/v1/tickets/" + ticketId + "/payment", resident);
        assertEquals("PAID_ONLINE", after.get("status").asText());
        assertTrue(after.get("receiptNumber").asText().startsWith("RCPT-"));

        // replaying the webhook is idempotent
        rest.postForEntity("/dev/pay/" + ref + "/settle", null, Void.class);
        assertEquals("PAID_ONLINE",
                get("/api/v1/tickets/" + ticketId + "/payment", resident).get("status").asText());
    }

    @Test
    void badWebhookSignatureIsRejected() {
        var mk = marketplace("+919000000111", "+919000000311");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000082", "Om");
        String ticketId = resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());
        post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.providerToken(), Map.of("amount", 400));
        JsonNode mode = post("/api/v1/tickets/" + ticketId + "/payment/mode", resident, Map.of("mode", "ONLINE"));
        String ref = mode.get("payLink").asText().replaceAll(".*/dev/pay/", "");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Stub-Signature", "deadbeef");
        var resp = rest.exchange("/api/v1/payments/webhook/stub", HttpMethod.POST,
                new HttpEntity<>("{\"ref\":\"" + ref + "\",\"paid\":true}", h), String.class);
        assertEquals(400, resp.getStatusCode().value());

        assertEquals("PENDING", get("/api/v1/tickets/" + ticketId + "/payment", resident).get("status").asText());
    }
}
