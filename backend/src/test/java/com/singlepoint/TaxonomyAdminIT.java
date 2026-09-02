package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaxonomyAdminIT extends IntegrationTestBase {

    @Test
    void superAdminOpensAVerticalAndCategory() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenant, "+919000000101", "GM Admin");
        String admin = login("+919000000101").token();

        JsonNode kind = post("/api/v1/superadmin/vendor-category-kinds", su,
                Map.of("code", "pet care", "label", "Pet Care"));
        assertEquals("PET_CARE", kind.get("code").asText());

        JsonNode cat = post("/api/v1/superadmin/vendor-categories", su,
                Map.of("name", "Dog Grooming", "kind", "PET_CARE"));
        String catId = cat.get("id").asText();

        // shows up grouped for everyone else
        JsonNode listed = get("/api/v1/vendor-categories", admin);
        assertTrue(anyMatch(listed, catId, "Pet Care"));

        // and a provider can be created under it
        JsonNode p = post("/api/v1/admin/providers", admin, Map.of(
                "name", "Happy Paws", "vendorCategoryId", catId, "company", true, "contactPhone", "+919000000901"));
        assertEquals(catId, p.get("vendorCategoryId").asText());

        // deactivating removes it from the picker but not from the provider row
        post("/api/v1/superadmin/vendor-categories/" + catId + "/deactivate", su, Map.of());
        assertFalse(anyMatch(get("/api/v1/vendor-categories", admin), catId, null));
        assertEquals(catId, get("/api/v1/admin/providers", admin).get(0).get("vendorCategoryId").asText());
    }

    @Test
    void onlySuperAdminMayManageTheTaxonomy() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Lakeview", "#1565C0");
        createAdmin(su, tenant, "+919000000201", "LV Admin");
        String admin = login("+919000000201").token();

        var r = http(HttpMethod.POST, "/api/v1/superadmin/vendor-category-kinds", admin,
                Map.of("code", "X", "label", "X"));
        assertEquals(403, r.getStatusCode().value());

        var badKind = http(HttpMethod.POST, "/api/v1/superadmin/vendor-categories", su,
                Map.of("name", "Ghost", "kind", "DOES_NOT_EXIST"));
        assertEquals(400, badKind.getStatusCode().value());
    }

    private boolean anyMatch(JsonNode arr, String id, String kindLabel) {
        for (JsonNode n : arr) {
            if (n.get("id").asText().equals(id)) {
                return kindLabel == null || kindLabel.equals(n.get("kindLabel").asText());
            }
        }
        return false;
    }
}
