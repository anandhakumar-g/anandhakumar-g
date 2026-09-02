package com.singlepoint;

import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVP-5 (B): ticket notifications also go over WhatsApp when the community's plan carries the
 * WHATSAPP_NOTIFICATIONS entitlement AND the recipient has opted in. Push is unchanged.
 */
class WhatsAppChannelIT extends IntegrationTestBase {

    private Marketplace mk;
    private String residentToken;
    private String residentUserId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
        residentUserId = get("/api/v1/me", residentToken).get("userId").asText();
        post("/api/v1/me/devices", residentToken,
                Map.of("token", "ExponentPushToken[ravi]", "platform", "android"));
    }

    private long channelCount(String channel) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ?::uuid and channel = ? "
                + "and template like 'TICKET%'", Long.class, residentUserId, channel);
    }

    private void assignStandardPlan() {
        post("/api/v1/superadmin/subscriptions", mk.superToken(), Map.of(
                "subjectType", "TENANT", "subjectId", mk.tenantId().toString(),
                "planCode", "TENANT_STANDARD", "comp", true));
    }

    private void raiseAndAcknowledge() {
        String cat = firstCategoryId(residentToken, "Electrical");
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "A-1")).get("id").asText();
        post("/api/v1/tickets/" + id + "/acknowledge", mk.adminToken(), Map.of());
        awaitOutboxDrained();
    }

    @Test
    void entitledAndOptedInGetsBothPushAndWhatsApp() {
        assignStandardPlan();
        put("/api/v1/me/notification-preferences", residentToken, Map.of("whatsappEnabled", true));
        raiseAndAcknowledge();

        assertTrue(channelCount("PUSH") >= 1);
        assertTrue(channelCount("WHATSAPP") >= 1);
    }

    @Test
    void notOptedInGetsPushOnly() {
        assignStandardPlan();
        raiseAndAcknowledge();

        assertTrue(channelCount("PUSH") >= 1);
        assertEquals(0, channelCount("WHATSAPP"));
    }

    @Test
    void entitlementWinsOverOptIn() {
        // no plan assigned → TENANT_FREE default, WHATSAPP_NOTIFICATIONS:0
        put("/api/v1/me/notification-preferences", residentToken, Map.of("whatsappEnabled", true));
        raiseAndAcknowledge();

        assertTrue(channelCount("PUSH") >= 1);
        assertEquals(0, channelCount("WHATSAPP"));
    }
}
