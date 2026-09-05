package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.security.TenantContext;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-10 (C): a community-less direct booking (MVP-8) can now be charged and paid. */
class CommunitylessPaymentIT extends IntegrationTestBase {

    private Marketplace mk;
    private String nomad;
    private String ticketId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        nomad = individualUser("+919888500001", "Nomad");
        ticketId = bookGlobalProvider(nomad, mk.providerId(), "Electrical");
        post("/api/v1/tickets/" + ticketId + "/accept", mk.providerToken(), Map.of());
        post("/api/v1/tickets/" + ticketId + "/status", mk.providerToken(), Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + ticketId + "/status", mk.providerToken(),
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "done"));
    }

    @Test
    void providerChargesResidentPaysCashReceiptIsTenantless() {
        JsonNode charged = post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.providerToken(),
                Map.of("amount", 300, "note", "parts + labour"));
        assertEquals("PENDING", charged.get("status").asText());

        post("/api/v1/tickets/" + ticketId + "/payment/mode", nomad, Map.of("mode", "CASH"));
        JsonNode collect = post("/api/v1/tickets/" + ticketId + "/payment/cash/collect", mk.providerToken(), Map.of());
        String otp = collect.get("devOtp").asText();

        JsonNode confirmed = post("/api/v1/tickets/" + ticketId + "/payment/cash/confirm", nomad, Map.of("otp", otp));
        assertEquals("PAID_CASH", confirmed.get("status").asText());

        JsonNode receipt = get("/api/v1/tickets/" + ticketId + "/payment/receipt", nomad);
        assertNotNull(receipt.get("receiptNumber").asText());

        TenantContext.setWildcard();
        long tenantless;
        try {
            tenantless = jdbcTemplate.queryForObject(
                    "select count(*) from payment_receipt where ticket_payment_id in "
                            + "(select id from ticket_payment where ticket_id = ?::uuid) and tenant_id is null",
                    Long.class, ticketId);
        } finally {
            TenantContext.clear();
        }
        assertEquals(1, tenantless);
    }

    @Test
    void anUnrelatedAdminOrProviderCannotReachThePayment() {
        post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.providerToken(), Map.of("amount", 300, "note", "x"));

        // the Green Meadows admin isn't a party to this tenant-less ticket
        assertEquals(404, http(HttpMethod.PUT, "/api/v1/tickets/" + ticketId + "/payment/charge",
                mk.adminToken(), Map.of("amount", 400, "note", "adjust")).getStatusCode().value());

        // a different, unrelated provider can't read it either
        var other = marketplace("+919000000111", "+919000000311");
        assertEquals(404, http(HttpMethod.GET, "/api/v1/tickets/" + ticketId + "/payment",
                other.providerToken(), null).getStatusCode().value());
    }
}
