package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VendorTaxonomyIT extends IntegrationTestBase {

    @Test
    void vendorCategoriesCoverTheNewVerticals() {
        String su = login(SUPER_ADMIN_PHONE).token();
        JsonNode cats = get("/api/v1/vendor-categories", su);
        Set<String> kinds = new HashSet<>();
        cats.forEach(c -> kinds.add(c.get("kind").asText()));
        assertTrue(kinds.contains("MAINTENANCE"));
        assertTrue(kinds.contains("FOOD_DINING"));
        assertTrue(kinds.contains("EVENTS_ENTERTAINMENT"));
        // every row carries a human label used for grouping
        cats.forEach(c -> assertFalse(c.get("kindLabel").asText().isBlank()));
    }

    @Test
    void providerCanBeCreatedUnderAFoodVertical() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenant, "+919000000101", "GM Admin");
        String admin = login("+919000000101").token();

        JsonNode p = post("/api/v1/superadmin/providers", su, Map.of(
                "name", "Spice Route Caterers", "vendorCategoryId", VENDOR_CAT_RESTAURANT,
                "company", true, "contactPhone", "+919000000701"));
        assertEquals("Spice Route Caterers", p.get("name").asText());
        assertEquals(VENDOR_CAT_RESTAURANT, p.get("vendorCategoryId").asText());
    }
}
