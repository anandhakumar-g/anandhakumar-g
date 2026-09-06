package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (A1): a community-less resident has a billing subject — RESIDENT_FREE, no paid plan. */
class ResidentBillingIT extends IntegrationTestBase {

    @Test
    void residentSeesTheFreeResidentPlan() {
        String resident = individualUser("+919888100201", "Nadia");

        JsonNode billing = get("/api/v1/me/billing", resident);
        assertEquals("RESIDENT", billing.get("subjectType").asText());
        assertEquals("RESIDENT_FREE", billing.get("plan").get("code").asText());
        assertTrue(billing.get("plan").get("entitlements").isEmpty());
        assertTrue(billing.get("dueInvoices").isEmpty());
    }

    @Test
    void residentCannotSelfUpgrade() {
        String resident = individualUser("+919888100202", "Omar");
        var r = http(HttpMethod.POST, "/api/v1/me/billing/plan", resident, Map.of("planCode", "TENANT_STANDARD"));
        assertEquals(400, r.getStatusCode().value());
        assertTrue(r.getBody().get("message").asText().toLowerCase().contains("resident plan"));
    }
}
