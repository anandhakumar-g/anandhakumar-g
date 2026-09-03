package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-7 (A/S3): a Super Admin assumes a community's scope to perform admin work, then steps back. */
class SuperAdminAsAdminIT extends IntegrationTestBase {

    private String su;
    private UUID tenant;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenant = createTenant(su, "Adminless Community", "#2E7D32");
    }

    @Test
    void superAdminActsAsAdminForAnAdminLessCommunity() {
        // no admin route works without an assumed tenant
        var noScope = http(HttpMethod.GET, "/api/v1/admin/flats", su, null);
        assertEquals(403, noScope.getStatusCode().value());

        JsonNode s = post("/api/v1/me/active-community", su, Map.of("tenantId", tenant.toString()));
        String acting = s.get("token").asText();
        assertEquals("SUPER_ADMIN", s.get("user").get("role").asText());
        assertEquals(tenant.toString(), s.get("user").get("activeTenantId").asText());

        // admin work now succeeds, scoped to the assumed community
        String locId = post("/api/v1/admin/locations", acting, Map.of("label", "Main")).get("id").asText();
        String flatId = post("/api/v1/admin/flats", acting,
                Map.of("locationId", locId, "block", "A", "flatNumber", "1")).get("id").asText();
        assertEquals(1, get("/api/v1/admin/flats", acting).size());
        assertEquals(tenant.toString(), get("/api/v1/me", acting).get("activeTenantId").asText());

        // step back to platform scope
        String platform = post("/api/v1/me/stop-acting", acting, Map.of()).get("token").asText();
        assertFalse(get("/api/v1/me", platform).hasNonNull("activeTenantId"));
        assertEquals(403, http(HttpMethod.GET, "/api/v1/admin/flats", platform, null).getStatusCode().value());
        assertNotNull(flatId);
    }
}
