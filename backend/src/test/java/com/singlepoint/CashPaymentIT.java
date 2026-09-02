package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CashPaymentIT extends IntegrationTestBase {

    @Test
    void cashWithOtpHappyPath() {
        var mk = marketplace("+919000000101", "+919000000301");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000071", "Deepa");
        String ticketId = resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());

        // provider adds the charge
        JsonNode p = post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.providerToken(),
                Map.of("amount", 500, "note", "socket + labour"));
        assertEquals("PENDING", p.get("status").asText());

        // resident sees it, chooses cash
        assertEquals("PENDING", get("/api/v1/tickets/" + ticketId + "/payment", resident).get("status").asText());
        post("/api/v1/tickets/" + ticketId + "/payment/mode", resident, Map.of("mode", "CASH"));

        // provider triggers the OTP (dev mode returns it)
        JsonNode collect = post("/api/v1/tickets/" + ticketId + "/payment/cash/collect", mk.providerToken(), Map.of());
        assertEquals("CASH_PENDING_OTP", collect.get("status").asText());
        String otp = collect.get("devOtp").asText();

        // wrong OTP is rejected, status unchanged
        var bad = http(HttpMethod.POST, "/api/v1/tickets/" + ticketId + "/payment/cash/confirm",
                mk.providerToken(), Map.of("otp", "000000"));
        assertEquals(400, bad.getStatusCode().value());
        assertEquals("SP-400-OTP", bad.getBody().get("errorCode").asText());
        assertEquals("CASH_PENDING_OTP",
                get("/api/v1/tickets/" + ticketId + "/payment", resident).get("status").asText());

        // the provider may submit the OTP the resident read out
        JsonNode done = post("/api/v1/tickets/" + ticketId + "/payment/cash/confirm", mk.providerToken(),
                Map.of("otp", otp));
        assertEquals("PAID_CASH", done.get("status").asText());

        JsonNode receipt = get("/api/v1/tickets/" + ticketId + "/payment/receipt", resident);
        assertTrue(receipt.get("receiptNumber").asText().startsWith("RCPT-"));
        assertTrue(receipt.get("shareText").asText().contains("Payment Receipt"));
        assertEquals("CASH", receipt.get("mode").asText());
    }

    @Test
    void residentCanAlsoSubmitTheirOwnOtp() {
        var mk = marketplace("+919000000111", "+919000000311");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000072", "Ravi");
        String ticketId = resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());

        post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.providerToken(), Map.of("amount", 300));
        post("/api/v1/tickets/" + ticketId + "/payment/mode", resident, Map.of("mode", "CASH"));
        String otp = post("/api/v1/tickets/" + ticketId + "/payment/cash/collect", mk.providerToken(), Map.of())
                .get("devOtp").asText();

        JsonNode done = post("/api/v1/tickets/" + ticketId + "/payment/cash/confirm", resident, Map.of("otp", otp));
        assertEquals("PAID_CASH", done.get("status").asText());
    }
}
