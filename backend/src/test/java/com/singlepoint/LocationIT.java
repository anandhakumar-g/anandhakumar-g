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

/** MVP-7 (C): a community spans multiple locations; flats hang off one; the table is RLS-scoped. */
class LocationIT extends IntegrationTestBase {

    private String su;
    private UUID tenantA;
    private String adminA;
    private UUID tenantB;
    private String adminB;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenantA = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenantA, "+919000000101", "GM Admin");
        adminA = login("+919000000101").token();
        tenantB = createTenant(su, "Lakeview", "#1565C0");
        createAdmin(su, tenantB, "+919000000201", "LV Admin");
        adminB = login("+919000000201").token();
    }

    @Test
    void flatsHangOffALocation() {
        UUID loc = createLocation(adminA, "Tower A");
        JsonNode flat = post("/api/v1/admin/flats", adminA,
                Map.of("locationId", loc.toString(), "block", "A", "flatNumber", "101"));
        assertEquals(loc.toString(), flat.get("locationId").asText());
        assertEquals("Tower A", flat.get("locationLabel").asText());

        // a bogus location id is rejected
        var bogus = http(HttpMethod.POST, "/api/v1/admin/flats", adminA,
                Map.of("locationId", UUID.randomUUID().toString(), "block", "B", "flatNumber", "1"));
        assertEquals(404, bogus.getStatusCode().value());

        // a flat with no location is rejected
        var noLoc = http(HttpMethod.POST, "/api/v1/admin/flats", adminA,
                Map.of("block", "C", "flatNumber", "1"));
        assertEquals(400, noLoc.getStatusCode().value());
    }

    @Test
    void locationAddressIsEncryptedAtRest() {
        createLocation(adminA, "Tower A");
        put("/api/v1/admin/locations/" + firstLocationId(adminA), adminA,
                Map.of("address", "12 Whitefield Main Rd"));
        TenantContext.setWildcard();
        try {
            String enc = jdbcTemplate.queryForObject(
                    "select address_enc from location where tenant_id = ?::uuid", String.class, tenantA.toString());
            assertNotNull(enc);
            assertNotEquals("12 Whitefield Main Rd", enc, "address must be ciphertext at rest");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void locationsAreTenantIsolatedByRls() {
        createLocation(adminA, "Tower A");
        createLocation(adminB, "Block Z");

        // admin B's list never shows A's location
        for (JsonNode l : get("/api/v1/admin/locations", adminB)) {
            assertNotEquals("Tower A", l.get("label").asText());
        }

        // raw SQL under tenant A's scope cannot see tenant B's rows
        TenantContext.setTenant(tenantA);
        try {
            Long crossTenant = jdbcTemplate.queryForObject(
                    "select count(*) from location where tenant_id = ?::uuid", Long.class, tenantB.toString());
            assertEquals(0L, crossTenant);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID firstLocationId(String adminToken) {
        return UUID.fromString(get("/api/v1/admin/locations", adminToken).get(0).get("id").asText());
    }
}
