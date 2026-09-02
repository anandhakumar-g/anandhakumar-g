package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OfferApprovalIT extends IntegrationTestBase {

    @Test
    void superAdminIsTheValidatingAuthority_andEnquiryTargetingWorks() {
        var mk = marketplace("+919000000101", "+919000000301");
        String enquiryCat = firstCategoryId(mk.adminToken(), "Enquiry");

        String interested = joinResident(mk.tenantId(), mk.adminToken(), "+919888000031", "Ira");
        String notInterested = joinResident(mk.tenantId(), mk.adminToken(), "+919888000032", "Om");
        // "interested" raised an enquiry in the target category
        post("/api/v1/tickets", interested, Map.of(
                "categoryId", enquiryCat, "description", "Do you have caterers?", "serviceAddressText", "A-1"));

        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "ENQUIRY_BASED", "enquiryCategoryId", enquiryCat)));

        // admin cannot reach the super-admin queue
        var forbidden = http(HttpMethod.GET, "/api/v1/superadmin/offers", mk.adminToken(), null);
        assertEquals(403, forbidden.getStatusCode().value());

        JsonNode queue = get("/api/v1/superadmin/offers", mk.superToken());
        assertEquals(1, queue.size());
        assertEquals("PENDING_APPROVAL", queue.get(0).get("status").asText());

        JsonNode approved = post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
        assertEquals("ACTIVE", approved.get("status").asText());

        awaitOutboxDrained();
        // the enquiry resident was targeted (a notification row exists); the other was not
        assertTrue(notificationCount(userId(interested), "OFFER", "SENT")
                 + notificationCount(userId(interested), "OFFER", "SKIPPED") >= 1);
        assertEquals(0, notificationCount(userId(notInterested), "OFFER", "SENT")
                       + notificationCount(userId(notInterested), "OFFER", "SKIPPED"));
    }

    @Test
    void rejectPathRecordsAReason() {
        var mk = marketplace("+919000000111", "+919000000311");
        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "SINGLE_TENANT",
                        "tenantIds", List.of(mk.tenantId().toString()))));

        JsonNode rejected = post("/api/v1/superadmin/offers/" + offerId + "/reject", mk.superToken(),
                Map.of("reason", "Discount too aggressive"));
        assertEquals("REJECTED", rejected.get("status").asText());
        assertEquals("Discount too aggressive", rejected.get("rejectReason").asText());
    }

    private String userId(String token) {
        return get("/api/v1/me", token).get("userId").asText();
    }
}
