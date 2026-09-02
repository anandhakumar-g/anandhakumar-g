package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AntiFatigueIT extends IntegrationTestBase {

    @Test
    void optInByCategory_frequencyCap_andIndependentTicketOptOut() {
        var mk = marketplace("+919000000101", "+919000000301");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000041", "Nina");
        String residentId = get("/api/v1/me", resident).get("userId").asText();
        // a device token so "sent" is possible
        post("/api/v1/me/devices", resident, Map.of("token", "ExponentPushToken[nina]", "platform", "android"));

        // defaults are opt-in: no subscriptions
        JsonNode prefs = get("/api/v1/me/notification-preferences", resident);
        assertTrue(prefs.get("subscribedVendorCategoryIds").isEmpty());
        assertTrue(prefs.get("promoNotificationsEnabled").asBoolean());

        String target = "{\"targetType\":\"SINGLE_TENANT\",\"tenantIds\":[\"" + mk.tenantId() + "\"]}";

        // offer #1 — not subscribed -> suppressed
        approveTargetedOffer(mk, target);
        awaitOutboxDrained();
        assertEquals(0, notificationCount(residentId, "OFFER", "SENT"));
        assertTrue(notificationCount(residentId, "OFFER", "SKIPPED") >= 1);

        // subscribe to the offer's category, cap = 2/week (GET above already created the row)
        rest.put("/api/v1/me/notification-preferences", authJson(resident, Map.of(
                "subscribedVendorCategoryIds", List.of(VENDOR_CAT_RESTAURANT),
                "promoFrequencyCapPerWeek", 2)));

        approveTargetedOffer(mk, target);
        approveTargetedOffer(mk, target);
        awaitOutboxDrained();
        assertEquals(2, notificationCount(residentId, "OFFER", "SENT"));

        approveTargetedOffer(mk, target); // 3rd -> over the weekly cap
        awaitOutboxDrained();
        assertEquals(2, notificationCount(residentId, "OFFER", "SENT"));
        assertTrue(notificationCount(residentId, "OFFER", "SKIPPED") >= 2);

        // mute promos but keep ticket notifications on
        rest.put("/api/v1/me/notification-preferences", authJson(resident, Map.of(
                "promoNotificationsEnabled", false, "ticketNotificationsEnabled", true)));
        long ticketNotifsBefore = totalNonOfferNotifications(residentId);

        String cat = firstCategoryId(resident, "Plumbing");
        String ticketId = post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "leak", "serviceAddressText", "A-1")).get("id").asText();
        post("/api/v1/tickets/" + ticketId + "/assign", mk.adminToken(),
                Map.of("providerId", mk.providerId().toString()));
        awaitOutboxDrained();
        assertTrue(totalNonOfferNotifications(residentId) > ticketNotifsBefore,
                "ticket notifications must still flow when only promos are muted");
    }

    private void approveTargetedOffer(Marketplace mk, String targetJson) {
        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        try {
            com.fasterxml.jackson.databind.JsonNode t = new com.fasterxml.jackson.databind.ObjectMapper().readTree(targetJson);
            post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(), Map.of("target", t));
        } catch (Exception e) { throw new RuntimeException(e); }
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
    }

    private long totalNonOfferNotifications(String userId) {
        Long n = jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ?::uuid and template not like 'OFFER%'",
                Long.class, userId);
        return n == null ? 0 : n;
    }

    private org.springframework.http.HttpEntity<Object> authJson(String token, Object body) {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return new org.springframework.http.HttpEntity<>(body, h);
    }
}
