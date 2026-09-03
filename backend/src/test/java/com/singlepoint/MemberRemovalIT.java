package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.security.TenantContext;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-7 (A4/I8): admin removes a member, hard-blocked by open tickets / unsettled bills. */
class MemberRemovalIT extends IntegrationTestBase {

    private Marketplace mk;
    private UUID flatId;
    private String residentToken;
    private String residentUserId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        flatId = createFlat(mk.adminToken(), "A", "101");
        String code = createFlatInvite(mk.adminToken(), flatId);
        residentToken = completeProfile(login("+919888000010").token(), "Ravi", "ravi@example.com").token();
        residentToken = toSession(post("/api/v1/memberships/join", residentToken,
                Map.of("tenantId", mk.tenantId().toString(), "inviteCode", code))).token();
        residentUserId = get("/api/v1/me", residentToken).get("userId").asText();
    }

    @Test
    void openTicketThenUnsettledBillBlockRemovalUntilCleared() {
        String cat = firstCategoryId(residentToken, "Electrical");
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", cat, "description", "fix it", "flatId", flatId.toString(),
                "serviceAddressText", "A-101")).get("id").asText();

        JsonNode check1 = get("/api/v1/admin/members/" + residentUserId + "/removal-check", mk.adminToken());
        assertFalse(check1.get("removable").asBoolean());
        assertEquals(1, check1.get("openTickets").size());

        var blocked = http(HttpMethod.POST, "/api/v1/admin/members/" + residentUserId + "/remove",
                mk.adminToken(), Map.of());
        assertEquals(409, blocked.getStatusCode().value());

        // resolve + charge, still blocked by the unsettled bill
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(), Map.of("providerId", mk.providerId().toString()));
        post("/api/v1/tickets/" + id + "/accept", mk.providerToken(), Map.of());
        post("/api/v1/tickets/" + id + "/status", mk.providerToken(), Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + id + "/status", mk.providerToken(),
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "done"));
        post("/api/v1/tickets/" + id + "/payment/charge", mk.adminToken(), Map.of("amount", 500));
        post("/api/v1/tickets/" + id + "/close", residentToken, Map.of("rating", 5));

        JsonNode check2 = get("/api/v1/admin/members/" + residentUserId + "/removal-check", mk.adminToken());
        assertFalse(check2.get("removable").asBoolean());
        assertEquals(0, check2.get("openTickets").size());
        assertEquals(1, check2.get("unsettledPayments").size());

        // waive the bill -> now removable
        post("/api/v1/tickets/" + id + "/payment/waive", mk.adminToken(), Map.of("reason", "goodwill"));
        JsonNode check3 = get("/api/v1/admin/members/" + residentUserId + "/removal-check", mk.adminToken());
        assertTrue(check3.get("removable").asBoolean());

        assertEquals(204, http(HttpMethod.POST, "/api/v1/admin/members/" + residentUserId + "/remove",
                mk.adminToken(), Map.of()).getStatusCode().value());

        // membership EXITED, flat occupant cleared, user drops to onboarding but still logs in
        TenantContext.setWildcard();
        try {
            String occ = jdbcTemplate.queryForObject(
                    "select coalesce(current_occupant_user_id::text,'') from flat where id = ?::uuid",
                    String.class, flatId.toString());
            assertEquals("", occ);
        } finally {
            TenantContext.clear();
        }
        JsonNode me = get("/api/v1/me", login("+919888000010").token());
        assertFalse(me.hasNonNull("activeTenantId"));
    }

    @Test
    void superAdminActingAsAdminCanAlsoRemove() {
        String acting = post("/api/v1/me/active-community", mk.superToken(),
                Map.of("tenantId", mk.tenantId().toString())).get("token").asText();
        assertEquals(204, http(HttpMethod.POST, "/api/v1/admin/members/" + residentUserId + "/remove",
                acting, Map.of()).getStatusCode().value());
    }
}
