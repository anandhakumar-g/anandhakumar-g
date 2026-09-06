package com.singlepoint;

import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MVP-13 (B2): offer promos reach WhatsApp only when the resident opts in (and the community is entitled). */
class PromoWhatsAppIT extends IntegrationTestBase {

    private long promoWhatsApp(String userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ?::uuid and channel = 'WHATSAPP' "
                + "and template like 'OFFER%'", Long.class, userId);
    }

    private void approveRestaurantOffer(Marketplace mk) {
        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "SINGLE_TENANT", "tenantIds", List.of(mk.tenantId().toString()))));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
        awaitOutboxDrained();
    }

    @Test
    void optInGetsPromoWhatsAppOptOutDoesNot() {
        Marketplace mk = marketplace("+919000000171", "+919000000371");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888100071", "Sana");
        String uid = get("/api/v1/me", resident).get("userId").asText();
        post("/api/v1/me/devices", resident, Map.of("token", "ExponentPushToken[sana]", "platform", "android"));
        post("/api/v1/superadmin/subscriptions", mk.superToken(), Map.of(
                "subjectType", "TENANT", "subjectId", mk.tenantId().toString(),
                "planCode", "TENANT_STANDARD", "comp", true)); // WHATSAPP_NOTIFICATIONS: -1

        put("/api/v1/me/notification-preferences", resident, Map.of(
                "subscribedVendorCategoryIds", List.of(VENDOR_CAT_RESTAURANT),
                "promoWhatsappEnabled", true));

        approveRestaurantOffer(mk);
        assertTrue(promoWhatsApp(uid) >= 1, "opted-in resident gets the promo on WhatsApp");

        long before = promoWhatsApp(uid);
        put("/api/v1/me/notification-preferences", resident, Map.of("promoWhatsappEnabled", false));
        approveRestaurantOffer(mk);
        assertEquals(before, promoWhatsApp(uid), "opting out stops promo WhatsApp");
    }
}
