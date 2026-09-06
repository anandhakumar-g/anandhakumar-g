package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (C1): GET /me/export returns a JSON attachment of the caller's own data — and nobody else's. */
class AccountExportIT extends IntegrationTestBase {

    @Test
    void exportHasTheCallersDataOnly() {
        Marketplace mk = marketplace("+919000000191", "+919000000391");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888100091", "Tara");
        String other = joinResident(mk.tenantId(), mk.adminToken(), "+919888100092", "Uma");
        String otherId = get("/api/v1/me", other).get("userId").asText();

        String tid = resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());
        String ref = get("/api/v1/tickets/" + tid, resident).get("referenceCode").asText();
        post("/api/v1/tickets/" + tid + "/payment/charge", mk.adminToken(), Map.of("amount", 200, "note", "parts"));

        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_ELECTRICAL);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "ALL_TENANTS")));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
        post("/api/v1/offers/" + offerId + "/redeem", resident, Map.of("code", "DIWALI20"));

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(resident);
        ResponseEntity<String> r = rest.exchange("/api/v1/me/export", HttpMethod.GET,
                new HttpEntity<>(h), String.class);

        assertEquals(200, r.getStatusCode().value());
        assertTrue(r.getHeaders().getContentType().toString().startsWith("application/json"));
        assertTrue(r.getHeaders().getFirst("Content-Disposition").contains("attachment"));

        String body = r.getBody();
        assertTrue(body.contains(ref), "the raised ticket is in the export");
        assertTrue(body.contains("\"offerRedemptions\""));
        assertTrue(body.contains("\"statusHistory\""));
        assertFalse(body.contains(otherId), "no other user's data leaks in");
    }
}
