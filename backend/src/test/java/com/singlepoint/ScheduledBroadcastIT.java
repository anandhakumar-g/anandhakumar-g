package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.broadcast.BroadcastDispatchJob;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (B3): a broadcast can be scheduled, dispatched when due, or cancelled while pending. */
class ScheduledBroadcastIT extends IntegrationTestBase {

    @Autowired
    BroadcastDispatchJob dispatchJob;

    @Test
    void futureScheduleStaysPendingThenDispatches() {
        Marketplace mk = marketplace("+919000000181", "+919000000381");
        String su = mk.superToken();

        JsonNode b = post("/api/v1/superadmin/broadcasts", su, Map.of(
                "scope", "ALL_ADMINS", "title", "Later", "body", "Scheduled note",
                "scheduledFor", Instant.now().plusSeconds(3600).toString()));
        assertEquals("PENDING", b.get("status").asText());
        assertEquals(0, b.get("recipientCount").asInt());
        assertEquals(0, dispatchJob.runNow(), "not due yet");

        jdbcTemplate.update("update broadcast set scheduled_for = now() - interval '1 minute' where id = ?::uuid",
                b.get("id").asText());
        assertEquals(1, dispatchJob.runNow());

        JsonNode row = get("/api/v1/superadmin/broadcasts", su).get("content").get(0);
        assertEquals("SENT", row.get("status").asText());
        assertTrue(row.get("recipientCount").asInt() >= 1);
        assertEquals(0, dispatchJob.runNow(), "idempotent — already SENT");
    }

    @Test
    void immediateSendIgnoresAPastSchedule() {
        Marketplace mk = marketplace("+919000000182", "+919000000382");
        JsonNode b = post("/api/v1/superadmin/broadcasts", mk.superToken(), Map.of(
                "scope", "ALL_ADMINS", "title", "Now", "body", "Immediate",
                "scheduledFor", Instant.now().minusSeconds(60).toString()));
        assertEquals("SENT", b.get("status").asText());
    }

    @Test
    void aPendingBroadcastCanBeCancelled() {
        Marketplace mk = marketplace("+919000000183", "+919000000383");
        String su = mk.superToken();
        JsonNode b = post("/api/v1/superadmin/broadcasts", su, Map.of(
                "scope", "ALL_ADMINS", "title", "Oops", "body", "nvm",
                "scheduledFor", Instant.now().plusSeconds(3600).toString()));

        var del = http(HttpMethod.DELETE, "/api/v1/superadmin/broadcasts/" + b.get("id").asText(), su, null);
        assertEquals(204, del.getStatusCode().value());

        jdbcTemplate.update("update broadcast set scheduled_for = now() - interval '1 minute' where id = ?::uuid",
                b.get("id").asText());
        assertEquals(0, dispatchJob.runNow(), "a cancelled broadcast never goes out");
    }
}
