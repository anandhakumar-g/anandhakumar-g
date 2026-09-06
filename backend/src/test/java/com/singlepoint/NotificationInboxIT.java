package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (B1): the in-app inbox lists the caller's notifications and tracks read state. */
class NotificationInboxIT extends IntegrationTestBase {

    @Test
    void inboxTracksReadState() {
        Marketplace mk = marketplace("+919000000151", "+919000000351");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888100051", "Priya");
        resolvedTicket(resident, mk.adminToken(), mk.providerToken(), mk.providerId());
        awaitOutboxDrained();

        JsonNode list = get("/api/v1/me/notifications", resident);
        assertTrue(list.get("content").size() >= 1, "the resident got at least one notification");
        assertNull(list.get("content").get(0).get("readAt"));

        long unread = get("/api/v1/me/notifications/unread-count", resident).get("count").asLong();
        assertTrue(unread >= 1);
        assertEquals(unread, get("/api/v1/me", resident).get("unreadNotifications").asLong());
        assertEquals(unread, get("/api/v1/me/notifications?unreadOnly=true", resident).get("content").size());

        String nid = list.get("content").get(0).get("id").asText();
        post("/api/v1/me/notifications/" + nid + "/read", resident, Map.of());
        assertEquals(unread - 1, get("/api/v1/me/notifications/unread-count", resident).get("count").asLong());

        post("/api/v1/me/notifications/read-all", resident, Map.of());
        assertEquals(0, get("/api/v1/me/notifications/unread-count", resident).get("count").asLong());
        assertEquals(0, get("/api/v1/me", resident).get("unreadNotifications").asLong());
    }
}
