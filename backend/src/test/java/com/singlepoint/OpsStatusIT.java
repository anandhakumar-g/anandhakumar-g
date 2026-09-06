package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (C5): the backup-status endpoint is Super-Admin-only and reports "disabled" locally. */
class OpsStatusIT extends IntegrationTestBase {

    @Test
    void superAdminSeesDisabledStatus() {
        String su = login(SUPER_ADMIN_PHONE).token();
        JsonNode s = get("/api/v1/superadmin/backup-status", su);
        assertFalse(s.get("enabled").asBoolean());
        assertEquals(30, s.get("retentionDays").asInt());
    }

    @Test
    void notForANonSuperAdmin() {
        Marketplace mk = marketplace("+919000000211", "+919000000411");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888100211", "Zoe");
        assertEquals(403, http(HttpMethod.GET, "/api/v1/superadmin/backup-status", resident, null)
                .getStatusCode().value());
    }
}
