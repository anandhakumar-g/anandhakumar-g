package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A Super-Admin-set FEATURED tier floats a provider to the top of the directory. */
class FeaturedTierIT extends IntegrationTestBase {

    @Test
    void featuredProviderSortsFirstRegardlessOfName() {
        var mk = marketplace("+919000000101", "+919000000301"); // provider "Sparky ..."
        // a second provider whose name sorts ahead alphabetically
        UUID aardvark = createVerifiedProvider(mk.superToken(), "Aardvark Repairs", "+919000000401");
        enrolProvider(mk.adminToken(), aardvark);

        JsonNode before = get("/api/v1/admin/providers", mk.adminToken());
        assertEquals(aardvark.toString(), before.get(0).get("id").asText());

        // Super Admin features Sparky
        assertEquals(204, http(HttpMethod.POST, "/api/v1/superadmin/providers/" + mk.providerId() + "/tier",
                mk.superToken(), Map.of("tier", "FEATURED")).getStatusCode().value());

        JsonNode after = get("/api/v1/admin/providers", mk.adminToken());
        assertEquals(mk.providerId().toString(), after.get(0).get("id").asText());
        assertEquals("FEATURED", after.get(0).get("tier").asText());
    }

    @Test
    void onlySuperAdminMaySetTier() {
        var mk = marketplace("+919000000111", "+919000000311");
        var r = http(HttpMethod.POST, "/api/v1/superadmin/providers/" + mk.providerId() + "/tier",
                mk.adminToken(), Map.of("tier", "FEATURED"));
        assertEquals(403, r.getStatusCode().value());
    }
}
