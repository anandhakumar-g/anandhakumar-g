package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-9 (B): admin -> residents, Super Admin -> admins / all users; opt-out + rate guard. */
class BroadcastIT extends IntegrationTestBase {

    private String superToken;
    private java.util.UUID tenantA;
    private java.util.UUID tenantB;
    private String adminA;
    private String adminAUserId;
    private String residentA1;
    private String residentA1Id;
    private String residentA2Id;
    private String residentB1Id;

    @BeforeEach
    void fixture() {
        superToken = login(SUPER_ADMIN_PHONE).token();

        tenantA = createTenant(superToken, "Green Meadows", "#2E7D32");
        adminAUserId = createAdmin(superToken, tenantA, "+919000000101", "Green Admin");
        adminA = login("+919000000101").token();
        residentA1 = joinResident(tenantA, adminA, "+919888000041", "Asha");
        residentA1Id = userId(residentA1);
        residentA2Id = userId(joinResident(tenantA, adminA, "+919888000042", "Bala"));
        registerDevice(residentA1);

        tenantB = createTenant(superToken, "Lakeview", "#1565C0");
        String adminB = login(loginAdmin(tenantB, "+919000000201", "Lake Admin")).token();
        residentB1Id = userId(joinResident(tenantB, adminB, "+919888000061", "Ravi"));
    }

    @Test
    void adminAnnouncementReachesOnlyTheActingCommunity() {
        JsonNode v = post("/api/v1/admin/broadcasts", adminA,
                Map.of("title", "Water shutdown", "body", "Supply off tomorrow 10am-2pm"));
        assertEquals("COMMUNITY", v.get("scope").asText());
        assertEquals(2, v.get("recipientCount").asInt());
        assertEquals(tenantA.toString(), v.get("tenantId").asText());

        awaitOutboxDrained();
        assertEquals(1, broadcastRows(residentA1Id));          // reached (SENT — has a device)
        assertEquals(1, broadcastRows(residentA2Id));          // reached (SKIPPED — no device)
        assertEquals("SENT", oneBroadcastStatus(residentA1Id));
        assertEquals(0, broadcastRows(residentB1Id));          // other community — untouched

        assertEquals(1, get("/api/v1/admin/broadcasts", adminA).get("content").size());
        String adminBTok = login("+919000000201").token();
        assertEquals(0, get("/api/v1/admin/broadcasts", adminBTok).get("content").size());
    }

    @Test
    void superAdminAllUsersReachesEveryoneIncludingACommunitylessNomad() {
        String nomadId = userId(individualUser("+919888500001", "Nomad"));

        post("/api/v1/superadmin/broadcasts", superToken,
                Map.of("scope", "ALL_USERS", "title", "New app version", "body", "Update from the store"));
        awaitOutboxDrained();

        assertEquals(1, broadcastRows(nomadId));
        assertEquals(1, broadcastRows(residentA1Id));
        assertEquals(1, broadcastRows(residentB1Id));
    }

    @Test
    void allAdminsScopeReachesAdminsNotResidents() {
        post("/api/v1/superadmin/broadcasts", superToken,
                Map.of("scope", "ALL_ADMINS", "title", "Quarterly sync", "body", "Thursday 4pm"));
        awaitOutboxDrained();

        assertEquals(1, broadcastRows(adminAUserId));
        assertEquals(0, broadcastRows(residentA1Id));
    }

    @Test
    void aMutedResidentIsSkipped() {
        put("/api/v1/me/notification-preferences", residentA1, Map.of("broadcastEnabled", false));

        post("/api/v1/admin/broadcasts", adminA, Map.of("title", "Clubhouse closed", "body", "Maintenance"));
        awaitOutboxDrained();

        assertEquals("SKIPPED", oneBroadcastStatus(residentA1Id));
        assertEquals("broadcasts muted", jdbcTemplate.queryForObject(
                "select error from notification where user_id = ?::uuid and template = 'BROADCAST'",
                String.class, residentA1Id));
    }

    @Test
    void backToBackSendsAreRateLimited_andAdminsCannotGoPlatformWide() {
        post("/api/v1/admin/broadcasts", adminA, Map.of("title", "One", "body", "first"));
        assertEquals(429, http(HttpMethod.POST, "/api/v1/admin/broadcasts", adminA,
                Map.of("title", "Two", "body", "too soon")).getStatusCode().value());

        assertEquals(403, http(HttpMethod.POST, "/api/v1/superadmin/broadcasts", adminA,
                Map.of("scope", "ALL_USERS", "title", "x", "body", "y")).getStatusCode().value());
    }

    // ---- helpers ----

    private String loginAdmin(java.util.UUID tenantId, String phone, String name) {
        createAdmin(superToken, tenantId, phone, name);
        return phone;
    }

    private String userId(String token) {
        return get("/api/v1/me", token).get("userId").asText();
    }

    private void registerDevice(String token) {
        post("/api/v1/me/devices", token, Map.of("token", "expo-" + token.hashCode(), "platform", "ANDROID"));
    }

    private long broadcastRows(String userId) {
        Long n = jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ?::uuid and template = 'BROADCAST'",
                Long.class, userId);
        return n == null ? 0 : n;
    }

    private String oneBroadcastStatus(String userId) {
        return jdbcTemplate.queryForObject(
                "select status from notification where user_id = ?::uuid and template = 'BROADCAST'",
                String.class, userId);
    }
}
