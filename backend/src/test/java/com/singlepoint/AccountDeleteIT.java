package com.singlepoint;

import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (C2): DELETE /me soft-deletes + anonymizes, gated on open work and sole-admin. */
class AccountDeleteIT extends IntegrationTestBase {

    @Test
    void blockedWhileAnOpenRequestExists() {
        Marketplace mk = marketplace("+919000000201", "+919000000401");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888100201", "Vik");
        String cat = firstCategoryId(resident, "Electrical");
        post("/api/v1/tickets", resident, Map.of("categoryId", cat, "description", "x", "serviceAddressText", "A-1"));

        var r = http(HttpMethod.DELETE, "/api/v1/me", resident, null);
        assertEquals(409, r.getStatusCode().value());
        assertTrue(r.getBody().get("message").asText().toLowerCase().contains("open request"));
    }

    @Test
    void soleAdminMustHandOver() {
        Marketplace mk = marketplace("+919000000202", "+919000000402");
        var r = http(HttpMethod.DELETE, "/api/v1/me", mk.adminToken(), null);
        assertEquals(409, r.getStatusCode().value());
        assertTrue(r.getBody().get("message").asText().toLowerCase().contains("only admin"));
    }

    @Test
    void deleteTombstonesTheRowKillsTheTokenAndFreesThePhone() {
        Marketplace mk = marketplace("+919000000203", "+919000000403");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888100203", "Rhea");
        String uid = get("/api/v1/me", resident).get("userId").asText();

        assertEquals(204, http(HttpMethod.DELETE, "/api/v1/me", resident, null).getStatusCode().value());

        assertEquals("Deleted user",
                jdbcTemplate.queryForObject("select name from app_user where id = ?::uuid", String.class, uid));
        assertNotNull(jdbcTemplate.queryForObject(
                "select deleted_at from app_user where id = ?::uuid", java.sql.Timestamp.class, uid));

        // the old token is dead immediately
        assertEquals(401, http(HttpMethod.GET, "/api/v1/me", resident, null).getStatusCode().value());

        // the phone number can start a fresh account
        String freshToken = completeProfile(login("+919888100203").token(), "Rhea Again", "rhea2@example.com").token();
        String freshUid = get("/api/v1/me", freshToken).get("userId").asText();
        assertNotEquals(uid, freshUid);
    }
}
