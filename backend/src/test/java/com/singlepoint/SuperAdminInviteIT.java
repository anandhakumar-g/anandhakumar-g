package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-7 (S7): a Super Admin issues invite codes directly for an admin-less community. */
class SuperAdminInviteIT extends IntegrationTestBase {

    private String su;
    private UUID tenant;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenant = createTenant(su, "Adminless Community", "#2E7D32");
    }

    @Test
    void superAdminIssuedCodeLetsAResidentJoin() {
        JsonNode c = post("/api/v1/superadmin/tenants/" + tenant + "/invite-codes", su,
                Map.of("maxUses", 5, "validDays", 30));
        String code = c.get("code").asText();
        assertEquals("ADMIN", c.get("kind").asText());

        // it shows up in the community's list
        boolean listed = false;
        for (JsonNode row : get("/api/v1/superadmin/tenants/" + tenant + "/invite-codes", su)) {
            if (row.get("code").asText().equals(code)) listed = true;
        }
        assertTrue(listed);

        // a fresh resident redeems it
        String tok = completeProfile(login("+919888000021").token(), "Meera", "meera@example.com").token();
        JsonNode joined = post("/api/v1/memberships/join", tok,
                Map.of("tenantId", tenant.toString(), "inviteCode", code));
        assertEquals("READY", joined.get("onboardingState").asText());
        assertEquals(tenant.toString(), joined.get("user").get("activeTenantId").asText());

        // revoke
        UUID codeId = UUID.fromString(
                get("/api/v1/superadmin/tenants/" + tenant + "/invite-codes", su).get(0).get("id").asText());
        var r = http(HttpMethod.DELETE,
                "/api/v1/superadmin/tenants/" + tenant + "/invite-codes/" + codeId, su, null);
        assertEquals(204, r.getStatusCode().value());
    }
}
