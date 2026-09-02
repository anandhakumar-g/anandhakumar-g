package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-5 (A5): Super Admin owns the global catalogue; a community admin manages its own list only when enabled. */
class CategoryAdminIT extends IntegrationTestBase {

    private String su;
    private UUID tenantA;
    private String adminA;
    private String residentA;
    private UUID tenantB;
    private String residentB;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenantA = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenantA, "+919000000101", "GM Admin");
        adminA = login("+919000000101").token();
        residentA = joinResident(tenantA, adminA, "+919888000010", "Ravi");

        tenantB = createTenant(su, "Lakeview", "#1565C0");
        createAdmin(su, tenantB, "+919000000201", "LV Admin");
        String adminB = login("+919000000201").token();
        residentB = joinResident(tenantB, adminB, "+919888000020", "Meena");
    }

    private boolean categoryVisible(String token, String name) {
        for (JsonNode c : get("/api/v1/categories", token)) {
            if (c.get("name").asText().equals(name)) return true;
        }
        return false;
    }

    @Test
    void superAdminGlobalCategoryIsVisibleEverywhere() {
        post("/api/v1/superadmin/ticket-categories", su,
                Map.of("name", "Fire Safety", "requestType", "ISSUE", "slaHours", 4));
        assertTrue(categoryVisible(residentA, "Fire Safety"));
        assertTrue(categoryVisible(residentB, "Fire Safety"));
    }

    @Test
    void superAdminTenantScopedCategoryIsVisibleOnlyThere() {
        post("/api/v1/superadmin/ticket-categories?tenantId=" + tenantA, su,
                Map.of("name", "Clubhouse Booking", "requestType", "ENQUIRY"));
        assertTrue(categoryVisible(residentA, "Clubhouse Booking"));
        assertFalse(categoryVisible(residentB, "Clubhouse Booking"));
    }

    @Test
    void duplicateNameAndBadRequestTypeAreRejected() {
        post("/api/v1/superadmin/ticket-categories", su,
                Map.of("name", "Gardening", "requestType", "ISSUE"));
        var dup = http(HttpMethod.POST, "/api/v1/superadmin/ticket-categories", su,
                Map.of("name", "gardening", "requestType", "ISSUE"));
        assertEquals(409, dup.getStatusCode().value());

        var bad = http(HttpMethod.POST, "/api/v1/superadmin/ticket-categories", su,
                Map.of("name", "Whatever", "requestType", "NONSENSE"));
        assertEquals(400, bad.getStatusCode().value());
        assertEquals("SP-400-VALIDATION", bad.getBody().get("errorCode").asText());
    }

    @Test
    void communityAdminNeedsTheFlagAndIsScopedToItsOwnTenant() {
        // flag off (default) → 403
        var denied = http(HttpMethod.POST, "/api/v1/admin/ticket-categories", adminA,
                Map.of("name", "Society Notice", "requestType", "FEEDBACK"));
        assertEquals(403, denied.getStatusCode().value());

        // Super Admin flips the community to self-managed
        put("/api/v1/superadmin/tenants/" + tenantA, su, Map.of("categoryAdmin", "COMMUNITY"));

        JsonNode created = post("/api/v1/admin/ticket-categories", adminA,
                Map.of("name", "Society Notice", "requestType", "FEEDBACK"));
        assertEquals(tenantA.toString(), created.get("tenantId").asText());
        assertTrue(categoryVisible(residentA, "Society Notice"));
        assertFalse(categoryVisible(residentB, "Society Notice"));

        // a global row is invisible to the community editor
        String globalId = post("/api/v1/superadmin/ticket-categories", su,
                Map.of("name", "Global Only", "requestType", "ISSUE")).get("id").asText();
        var reach = http(HttpMethod.PUT, "/api/v1/admin/ticket-categories/" + globalId, adminA,
                Map.of("name", "Hijacked"));
        assertEquals(404, reach.getStatusCode().value());
    }

    @Test
    void deactivateHidesFromThePickerAndReactivateBringsItBack() {
        String id = post("/api/v1/superadmin/ticket-categories", su,
                Map.of("name", "Seasonal", "requestType", "ISSUE")).get("id").asText();
        assertTrue(categoryVisible(residentA, "Seasonal"));

        post("/api/v1/superadmin/ticket-categories/" + id + "/deactivate", su, Map.of());
        assertFalse(categoryVisible(residentA, "Seasonal"));

        post("/api/v1/superadmin/ticket-categories/" + id + "/reactivate", su, Map.of());
        assertTrue(categoryVisible(residentA, "Seasonal"));
    }

    @Test
    void raisingAgainstAnotherTenantsCategoryIs404() {
        String bId = post("/api/v1/superadmin/ticket-categories?tenantId=" + tenantB, su,
                Map.of("name", "B Only", "requestType", "ISSUE")).get("id").asText();

        var r = http(HttpMethod.POST, "/api/v1/tickets", residentA, Map.of(
                "categoryId", bId, "description", "x", "serviceAddressText", "A-1"));
        assertEquals(404, r.getStatusCode().value());
    }
}
