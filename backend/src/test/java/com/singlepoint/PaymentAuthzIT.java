package com.singlepoint;

import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentAuthzIT extends IntegrationTestBase {

    @Test
    void chargeModeAndWaiveAreRoleGuarded() {
        var mk = marketplace("+919000000101", "+919000000301");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000091", "Asha");
        String ticketId = resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());

        // resident cannot add a charge
        var r1 = http(HttpMethod.POST, "/api/v1/tickets/" + ticketId + "/payment/charge", resident,
                Map.of("amount", 100));
        assertEquals(403, r1.getStatusCode().value());

        // admin adds it; only the resident can choose a mode
        post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.adminToken(), Map.of("amount", 100));
        var r2 = http(HttpMethod.POST, "/api/v1/tickets/" + ticketId + "/payment/mode", mk.adminToken(),
                Map.of("mode", "CASH"));
        assertEquals(403, r2.getStatusCode().value());

        // only an admin can waive
        var r3 = http(HttpMethod.POST, "/api/v1/tickets/" + ticketId + "/payment/waive", mk.providerToken(),
                Map.of("reason", "nope"));
        assertEquals(403, r3.getStatusCode().value());
        assertEquals("WAIVED", post("/api/v1/tickets/" + ticketId + "/payment/waive", mk.adminToken(),
                Map.of("reason", "goodwill")).get("status").asText());
    }

    @Test
    void chargeRequiresAResolvedTicketAndIsTenantScoped() {
        var mk = marketplace("+919000000111", "+919000000311");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000092", "Bala");
        String cat = firstCategoryId(resident, "Electrical");
        String openTicket = post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "A-1")).get("id").asText();

        var early = http(HttpMethod.POST, "/api/v1/tickets/" + openTicket + "/payment/charge", mk.adminToken(),
                Map.of("amount", 100));
        assertEquals(409, early.getStatusCode().value());

        // resolve it, charge it, then a different tenant's admin cannot see the payment
        String ticketId = resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());
        post("/api/v1/tickets/" + ticketId + "/payment/charge", mk.adminToken(), Map.of("amount", 250));

        var other = marketplace("+919000000121", "+919000000321");
        var cross = http(HttpMethod.GET, "/api/v1/tickets/" + ticketId + "/payment", other.adminToken(), null);
        assertEquals(404, cross.getStatusCode().value());
    }
}
